# Migrating a live Supabase app to AWS

## The scenario

This app does not use Supabase — it runs on plain Postgres. This document answers the adjacent
question: an existing product runs on Supabase (Postgres, Supabase Auth, Supabase Storage, and a few
scheduled Edge Functions) and needs to move onto the AWS stack described in
[`aws-architecture.md`](aws-architecture.md), **without downtime and without asking a single user to
reset their password**.

Where each Supabase piece lands:

| Supabase | AWS | Difficulty |
|---|---|---|
| Postgres | RDS PostgreSQL | Easy — logical replication |
| Supabase Auth | Cognito user pool | **Hard — this is the interesting part** |
| Supabase Storage | S3 + CloudFront | Easy — both are S3-compatible |
| Edge Functions / `pg_cron` | EventBridge Scheduler → Lambda or ECS | Medium — verify in parallel |
| Row Level Security | Application-level authorisation | Medium — RLS does not port |

## The organising principle

**Nothing is a one-way door until the final cutover.** Every phase below is reversible, writes stay
on Supabase until the very last step, and rollback is a routing change rather than a restore. If you
remember one thing from this document, it is that ordering — reads move before writes, and the
reverse replication stays armed.

## Phase 1 — Prepare the target

Stand up RDS PostgreSQL on the **same major version** as the Supabase instance. A version bump and a
platform migration at the same time makes any regression impossible to attribute.

Migrate the schema on its own first: `pg_dump --schema-only --no-owner --no-privileges`, then *read
the output* before applying it. Supabase installs extensions and roles that RDS does not have:

- `pgcrypto`, `uuid-ossp` — available on RDS, just need enabling.
- `pgjwt`, `pg_graphql` — **not** available. Anything depending on them moves into application code.
- The `auth.*` and `storage.*` schemas belong to Supabase's own services. Do not copy them into the
  application database; they are handled in phases 3 and 4.
- **Row Level Security policies referencing `auth.uid()` will not work.** RLS was doing
  authorisation inside the database against a Supabase-issued JWT claim. On RDS with Cognito that
  claim is gone. Either rewrite the policies against a claim you set per-connection, or — usually
  cleaner — lift authorisation into the API. Decide this early; it is the change most likely to
  be underestimated.

## Phase 2 — Move the data while the app keeps writing

Use **logical replication** — AWS DMS, or native `CREATE PUBLICATION` / `CREATE SUBSCRIPTION` if the
table set is simple enough not to justify DMS. Either way: an initial full load, then CDC streaming,
so RDS stays continuously in sync while **Supabase remains the system of record**.

Why not `pg_dump` / `pg_restore`: that means freezing writes for the duration of the copy, which on
any meaningful dataset is an outage. With CDC the cutover window shrinks from "however long the copy
takes" to a few seconds of replication lag.

Two gaps that bite everyone:

- **Sequences do not replicate.** Bump them past the current max before cutover or new inserts
  collide with replicated rows.
- **Tables without a primary key need `REPLICA IDENTITY FULL`**, or updates and deletes will not
  replicate.

## Phase 3 — Auth, with no forced password reset

This is the part that most obviously goes wrong, so it is worth being precise.

Supabase Auth stores **bcrypt** password hashes in `auth.users`. Cognito's bulk user import **cannot
accept bcrypt hashes** — it imports attributes only, and imported users land in
`FORCE_CHANGE_PASSWORD`. Importing and hoping produces exactly the forced-reset outcome we are
trying to avoid.

The mechanism that solves it is a **Cognito user migration Lambda trigger**:

1. Export `auth.users` — id, email, verified flag, metadata, and the bcrypt hash — into a store the
   Lambda can read (DynamoDB, or Secrets-Manager-brokered access to the old database).
2. Create the Cognito user pool **empty**, with a Lambda on the `UserMigration` trigger.
3. A user signs in for the first time. Cognito does not find them, so it invokes the Lambda with the
   submitted username and password.
4. The Lambda verifies that password against the exported bcrypt hash — or, during the transition
   window, by calling the Supabase Auth API — and on success returns the user's attributes.
5. Cognito silently creates the native user with that password and completes the sign-in.

From the user's side this is an ordinary login. Migration happens lazily, one user at a time, with no
announcement and no reset email.

Two things to plan for: the trigger fires on `ForgotPassword` as well as `Authenticate`, so handle
both. And users who never log in during the window are never migrated — after it closes, bulk-import
the remainder as `RESET_REQUIRED` and accept that the long tail does get an email.

## Phase 4 — Storage

Supabase Storage is S3-compatible, so the object copy is a scripted `aws s3 sync` (or `rclone`)
against the Supabase endpoint: one full pass, then repeated delta passes until the diff is small,
then a final pass at cutover.

Two application-side changes go with it. Public object URLs move behind CloudFront, so anything that
persisted a raw Supabase URL in the database needs rewriting — a one-time `UPDATE`, and ideally a
lesson about storing keys rather than URLs. Signed-URL generation moves from the Supabase SDK to S3
presigned URLs; the semantics are close enough that this is usually a small change.

## Phase 5 — Scheduled jobs

Supabase `pg_cron` jobs and scheduled Edge Functions become **EventBridge Scheduler** rules targeting
Lambda or ECS tasks.

Migrate these **before** cutover and run both sides in parallel against their respective databases,
comparing outputs for a few days. Scheduled jobs are the classic thing everyone forgets until the
first Monday after a migration; running them in parallel means the AWS versions are already proven
by the time they become the only ones.

## Phase 6 — Gradual cutover

**Reads shift before writes.**

Route 53 weighted routing — or, better for an app you control, a feature-flagged API base URL, since
weights change instantly and are not subject to DNS caching — moves traffic **5% → 25% → 50% →
100%**, pausing at each step long enough to compare error rates and p95 latency between the stacks.

Throughout this, replication is still flowing Supabase → RDS, so AWS is serving reads from data
Supabase still owns.

**Writes move in a single final step**, once 100% of read traffic is healthy on AWS. Splitting writes
across both stacks would mean dual-write conflicts and a reconciliation problem with no clean answer.
The apparent gradualism is not worth it.

## Phase 7 — Rollback and reconciliation

Before flipping writes, **reverse replication (RDS → Supabase) is armed**. That is what makes
rollback a routing change rather than a restore from backup: if something surfaces an hour after
cutover, Supabase already has the new writes and traffic simply goes back.

Reconciliation runs continuously through the transition:

- Row counts per table, both sides, on a schedule.
- A checksum per table — `md5(string_agg(...))` over a stable ordering — for anything financial or
  otherwise unforgiving.
- **One explicitly documented source of truth per phase**, so that when the two disagree there is no
  argument about which one wins.

Supabase is decommissioned only after **N clean days** past the write cutover — at least seven, to
cover a full weekly cycle including whatever runs at the weekend — with reverse replication armed
that whole time. Keeping it alive an extra week costs almost nothing next to discovering a problem
after the only copy of the old system is gone.
