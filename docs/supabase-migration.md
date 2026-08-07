# Migrating a live Supabase app to AWS

Scenario: an existing product runs on Supabase — Postgres, Supabase Auth, Supabase Storage, and a
few scheduled Edge Functions — and needs to move to the AWS stack in
[`aws-architecture.md`](aws-architecture.md) without downtime and without asking users to reset
their passwords.

The organising principle is that **nothing is a one-way door until the final cutover**. Every phase
below is reversible, and the writes stay on Supabase until the very last step.

## 1. Prepare the target

Stand up RDS PostgreSQL on the **same major version** as the Supabase instance — a version bump and
a platform migration at the same time makes any regression impossible to attribute.

Migrate the schema first, on its own: `pg_dump --schema-only --no-owner --no-privileges`, then read
the output before applying it. Supabase installs extensions and roles that do not exist on RDS. In
practice: `pgcrypto` and `uuid-ossp` are available on RDS and just need enabling; `pgjwt` and
`pg_graphql` are not, and anything depending on them has to move into application code; the
`auth.*` and `storage.*` schemas belong to Supabase's own services and should **not** be copied
into the application database — they are handled in steps 3 and 4. Row Level Security policies
referencing `auth.uid()` need rewriting against whatever claim the new JWT carries.

## 2. Move the data while the app keeps writing

Use **logical replication** — AWS DMS, or native `CREATE PUBLICATION` / `CREATE SUBSCRIPTION` if
the table set is simple enough not to justify DMS. Either way it is an initial full load followed
by CDC streaming, so RDS stays continuously in sync while Supabase remains the system of record.

Why not `pg_dump`/`pg_restore`: a dump-and-restore of a live database means freezing writes for the
duration of the copy, which on any meaningful dataset is an outage. With CDC the cutover window
shrinks from however long the copy takes to a few seconds of replication lag.

Watch for the usual logical-replication gaps: sequences do not replicate (bump them past the
current max before cutover, or new inserts collide), and tables without a primary key need
`REPLICA IDENTITY FULL`.

## 3. Auth, with no forced password reset

This is the part that most obviously goes wrong, so it is worth being precise.

Supabase Auth stores **bcrypt** password hashes in `auth.users`. Cognito's bulk user import cannot
accept bcrypt hashes — it imports user attributes only, and imported users land in
`FORCE_CHANGE_PASSWORD`. Importing and hoping is exactly the forced-reset outcome we are trying to
avoid.

The mechanism that solves this is a **Cognito user migration Lambda trigger**:

1. Export `auth.users` — id, email, verified flag, metadata, and the bcrypt hash — into a store the
   Lambda can read (DynamoDB, or Secrets-Manager-backed access to the old database).
2. Create the Cognito user pool **empty**, with a Lambda configured on the `UserMigration` trigger.
3. A user signs in for the first time. Cognito does not find them, so it invokes the Lambda with the
   submitted username and password.
4. The Lambda verifies the password against the exported bcrypt hash (or, during the transition
   window, by calling the Supabase Auth API), and on success returns the user's attributes.
5. Cognito silently creates the native user with that password and completes the sign-in.

From the user's side this is an ordinary login. Migration happens lazily, one user at a time, with
no announcement and no reset email.

Two things to plan for: the trigger fires on `ForgotPassword` as well as `Authenticate`, so handle
both; and users who never log in during the window are never migrated — after the window closes,
bulk-import the remainder as `RESET_REQUIRED` and accept that the long tail does get an email.

## 4. Storage

Supabase Storage is S3-compatible, so the object copy is a scripted `aws s3 sync` (or `rclone`)
against the Supabase endpoint, run incrementally: a first full pass, then repeated delta passes
until the diff is small, then a final pass at cutover.

Two application-side changes go with it. Public object URLs move behind CloudFront, so anything
that has persisted a raw Supabase URL in the database needs rewriting — a one-time `UPDATE`, and
ideally a lesson about storing keys rather than URLs. Signed-URL generation moves from Supabase's
SDK to S3 presigned URLs; the call sites are usually few and the semantics are close enough that
this is a small change.

## 5. Scheduled jobs

Supabase `pg_cron` jobs and scheduled Edge Functions become **EventBridge Scheduler** rules
targeting Lambda or ECS tasks.

Migrate these **before** cutover and run both sides in parallel against their respective databases,
comparing outputs for a few days. Scheduled jobs are the classic thing everyone forgets until the
first Monday after a migration, and running them in parallel means the AWS versions are already
proven when they become the only ones.

## 6. Gradual cutover

Reads shift before writes.

Route 53 weighted routing (or, better for an app you control, a feature-flagged API base URL —
weights change instantly and are not subject to DNS caching) moves traffic **5% → 25% → 50% → 100%**,
pausing at each step long enough to compare error rates and p95 latency between the two stacks.

Throughout this, replication is still flowing Supabase → RDS, so AWS is serving reads from data
that Supabase still owns. **Writes move in a single final step**, once 100% of read traffic is
healthy on AWS. Splitting writes across both stacks would mean dual-write conflicts and a
reconciliation problem with no clean answer, so it is not worth the apparent gradualism.

## 7. Rollback and reconciliation

Before flipping writes, **reverse replication (RDS → Supabase) is armed**. That is what makes
rollback a routing change rather than a restore from backup: if something goes wrong an hour after
cutover, Supabase already has the new writes and traffic can go back with no data loss.

Reconciliation runs continuously through the transition:

- Row counts per table, both sides, on a schedule.
- A checksum per table (`md5(string_agg(...))` over a stable ordering, or `pg_checksums` on the
  physical files) for anything financial or otherwise unforgiving.
- One explicitly documented source of truth per phase, so that when the two disagree there is no
  argument about which one wins.

Supabase is decommissioned only after **N clean days** past the write cutover — I would want at
least seven, covering a full weekly cycle including whatever runs on the weekend — with reverse
replication armed for that entire period. The cost of keeping it alive an extra week is trivially
small compared with discovering an issue after the only copy of the old system is gone.
