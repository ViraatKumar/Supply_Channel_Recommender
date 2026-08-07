# Supply Channel Recommender

Ranks applicant supply channels for a hiring campaign. You describe the role, headcount, budget and
timeline; it returns an ordered list of channels with expected cost, expected volume, a quality
estimate, a plain-English reason, and the limitations you should know about — plus every channel it
ruled out and the single rule that ruled it out.

Spring Boot (Java 21) + React (Vite). PostgreSQL for the channel catalogue. One optional AI feature
that parses a free-text hiring brief into the form; the app is fully functional without it.

---

## Running it

```bash
GEMINI_API_KEY=... docker compose up --build
```

Frontend on <http://localhost:5173>, API on <http://localhost:8080>. The API key is optional — leave
it out and everything works except the brief parser, which reports itself unavailable (see
[AI feature and fallback](#ai-feature-and-fallback)).

### Without Docker

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

```bash
cd frontend && npm install && npm run dev
```

The `h2` profile runs the whole backend on an in-memory database with the same schema and seed data,
so no Docker or Postgres is needed. Postgres is the default profile because it matches the RDS
Postgres proposed in [`docs/aws-architecture.md`](docs/aws-architecture.md) — the local and
proposed-production stories should not diverge.

### Tests

```bash
cd backend && mvn test
```

15 tests, all against the scoring engine. That is deliberate: the engine is the part with actual
judgement in it, and it is written so it can be tested without HTTP, Spring, or a database.

---

## Scoring methodology

Two phases: **hard exclusion**, then **weighted scoring**. Exclusion is kept out of the score on
purpose — folding "cannot be used" into a number produces channels that rank low but still appear,
and a recruiter cannot tell a mediocre option from an impossible one.

### Phase 1 — hard filters

A channel is removed outright, with its reason returned in the `excluded` list, if any of these
hold. Rules are evaluated most-fundamental-first and the first match wins, so a channel you can
neither afford nor wait for is reported as unaffordable — the more actionable answer.

| Rule | Condition |
|---|---|
| `MIN_BUDGET` | The channel's floor spend exceeds the campaign budget |
| `LEAD_TIME` | Lead time ≥ timeline, so there is no delivery window at all |
| `LOCATION` | The channel does not supply the requested location, and the role is not remote |
| `SKILL_OVERLAP` | Zero overlap between the required skills and the channel's tags |

`LEAD_TIME` uses `>=` rather than `>`: a channel whose setup exactly consumes the timeline produces
nothing, so it is as unusable as one that overruns.

### Phase 2 — five weighted factors

Every factor is normalised to 0–1 before any weight is applied, otherwise rupees would silently
outvote a 1–10 quality score. Each is computed from the channel and the request alone — never from
the other candidates — which is what makes a ranking reproducible and a regression test meaningful.

| Factor | Balanced weight | What it measures |
|---|---|---|
| Cost efficiency | 30% | Can the budget buy the headcount here, and with how much room to spare |
| Volume fit | 25% | Does throughput cover the headcount inside the delivery window |
| Quality | 25% | Editorial 1–10 applicant-relevance estimate |
| Speed | 15% | How much of the timeline survives the channel's setup |
| Skill match | 5% | Fraction of the requested skills the channel is known for |

**Why these numbers.** A hiring campaign is budget-constrained before it is anything else — if the
money cannot buy the headcount, the channel's other virtues are academic — so cost leads at 30%.
Volume and quality sit level at 25% each because they are the two things a recruiter genuinely
trades off, and pretending one dominates in the general case would be a lie. Speed gets 15%: it
matters, but a channel that misses the deadline entirely has already been removed by a hard filter,
so this weight only prices the difference between "fast" and "just in time". Skill match is a
residual 5% — zero overlap is a hard filter, so all it does is break ties toward the tighter fit.

Two factor shapes are worth calling out, because the obvious implementation is wrong in both cases:

- **Volume fit is capped at 1.0.** A channel that could produce ten times the applicants you asked
  for is not ten times better — the surplus is screening work, not value. Without the cap a firehose
  channel bulldozes every other factor.
- **Cost efficiency keeps rising past full coverage.** Plain coverage capped at 1.0 would score a
  ₹60 channel and a ₹400 channel identically whenever the budget comfortably covers both — exactly
  the discrimination a cost factor exists to provide. So the curve is piecewise: proportional and
  capped at 0.6 below break-even, then 0.6 at break-even rising to 1.0 at 2× budget headroom.

### The default is wrong twice, so the engine switches profiles

Balanced weights are wrong in two predictable situations, and the engine picks a different profile
automatically:

| Profile | Chosen when | Cost / Volume / Quality / Speed / Skill |
|---|---|---|
| `BALANCED` | default | 30 / 25 / 25 / 15 / 5 |
| `QUALITY_FIRST` | seniority is SENIOR or EXECUTIVE | 20 / 15 / **45** / 15 / 5 |
| `VOLUME_FIRST` | headcount ≥ 100 | 30 / **40** / 10 / 15 / 5 |

For senior hiring you are not filling a funnel, you are trying to find a handful of people who could
actually do the job — twenty bad senior CVs are worth less than two good ones, and cheap-per-applicant
is actively misleading at that level. For bulk hiring the risk inverts: once you need hundreds of
applicants the question is whether anyone applies at all, and screening load is a known accepted cost.

All of this lives in one file — [`WeightProfile.java`](backend/src/main/java/com/joveo/supply/scoring/WeightProfile.java).
Changing a ranking's priorities is a one-line edit to a weight table, not a change to the algorithm.
The UI exposes a manual override so you can watch the ranking move.

### Derived outputs

Expected delivery is the smallest of three numbers — what you asked for, what the budget buys, and
what the channel's throughput reaches inside the delivery window — and the response names which one
bound it (`DEMAND` / `BUDGET` / `VOLUME`). That is the honest answer to "why won't this channel just
give me everything I asked for?"

`reason` leads with the factor that contributed the most **weighted** score, not the highest raw
score — a perfect 1.0 on a 5%-weight factor did not decide the ranking and should not be offered as
the reason for it. `limitations` combines the channel's own stated caveat, the shortfall if the
channel cannot cover the full ask, and a note on quality or speed if either scores at or below 0.4.
Cost and volume weakness are not repeated there because they already surface as the binding
constraint.

Both are assembled from the same delivery estimate the score used, so the explanation and the
arithmetic cannot drift apart.

### Determinism

Same request + same catalogue ⇒ same list, same order, same sentences. No randomness, no model call,
no dependence on wall-clock time or catalogue order. Scores are rounded before ranking, not just
before display, so a tie you can see is resolved by the documented tiebreakers — quality desc, then
price asc, then name — rather than by invisible floating-point noise.

---

## AI feature and fallback

**What it does:** `POST /api/parse-brief` takes a free-text hiring brief and returns a draft
campaign request that pre-fills the form. It handles the things a recruiter actually writes —
"8 lakh" → 800000, "6 weeks" → 42 days, "Principal Engineer" → SENIOR — and returns a
`missingFields` list so the UI can say what still needs answering.

**Where it sits:** strictly *upstream* of the ranking. The model fills in a form; a human reviews and
submits it; the deterministic engine does the scoring. Nothing the model returns can change a rank,
a score, or a reason. Three layers of containment: a strict JSON schema constrains the response
shape, the parsed values are validated server-side against the domain types, and a human confirms
the form before it is submitted. `BriefDraft` is deliberately a separate type from `CampaignRequest`
so there is no code path by which model output reaches the engine unreviewed.

**Fallback.** `/api/parse-brief` always returns HTTP 200. If the key is missing, the call times out,
the model is rate-limited, or the JSON comes back malformed, the response is
`{"parsed": false, "message": "..."}` and the UI shows a notice above the untouched manual form. The
frontend's `parseBrief()` cannot throw either. A failed parse is a normal outcome the product
handles, not an error the user has to recover from — **the app is fully functional with no API key
at all.**

Model: `gemini-3.6-flash` via Spring AI, which maps the response onto `BriefDraft` from the record's
own shape. A fast, cheap model is the right call here — this is extraction, not reasoning, and the
user is waiting on it.

One wiring detail worth knowing: Spring AI builds its client bean eagerly at startup and refuses to
construct without a key, which would stop the whole app from booting when none is set. So
`spring.ai.google.genai.api-key` falls back to a placeholder, and the real switch is `app.ai.api-key`
— `BriefParser` checks that and never calls the model when it is blank.

---

## API

| Endpoint | Purpose |
|---|---|
| `POST /api/recommendations` | Ranked channels + excluded channels + the weights used |
| `POST /api/parse-brief` | Free-text brief → draft form values. Always 200 |
| `GET /api/channels` | The seeded channel catalogue |

```bash
curl -s localhost:8080/api/recommendations -H 'Content-Type: application/json' -d '{
  "jobTitle":"Principal Engineer","location":"Bengaluru","applicantsNeeded":15,
  "budget":18000,"timelineDays":12,"skills":["engineering"],"seniority":"SENIOR",
  "remoteOk":false,"additionalConstraints":null,"weightProfileOverride":null}'
```

---

## The dataset

Eight channels, seeded from [`data.sql`](backend/src/main/resources/data.sql), chosen so the scoring
visibly discriminates rather than producing a plausible-looking but flat ranking: LinkedIn, Naukri,
Indeed, Wellfound, a dev community Discord, a newsletter sponsorship, an employee referral program,
and Meta Ads. The spread is deliberate — high-quality/low-volume (referral, Discord), cheap
high-volume/low-quality (Indeed, Meta), location-restricted (Naukri, Meta), long lead time
(newsletter at 10 days), and a high minimum spend (newsletter at ₹40,000). Costs are INR per
applicant, order-of-magnitude realistic for the Indian market but editorial, not measured.

---

## Trade-offs and what I'd do next

- **Quality is an editorial 1–10 constant.** In production it would be a measured
  applicant-to-interview or applicant-to-hire conversion rate per channel *per role family* — the
  quality of Naukri for a warehouse role and for a staff engineer are not the same number. The
  scoring shape does not change; the input gets better.
- **Scores compress when the budget is ample.** If everything is affordable, cost efficiency
  saturates and the spread narrows to the quality and volume differences. That is honest rather than
  broken, but a normalised "relative to the best available option" view would read better in the UI.
- **Tag lists are stored comma-separated** rather than in a join table. The catalogue is a handful
  of read-only rows always loaded whole; a join table buys normalisation nothing is querying on, and
  it keeps the seed SQL portable between Postgres and H2.
- **No channel-mix optimisation.** The engine ranks channels individually; it does not solve for the
  best *portfolio* under one budget. That is the genuinely interesting next problem — it is a
  knapsack, and the right first version is a greedy allocation by marginal cost-per-quality-adjusted
  applicant until either the budget or the headcount runs out.
- **No persistence of campaigns or results.** Every request is stateless. Storing them is what would
  eventually close the loop and let measured performance replace the editorial quality score.

---

## Use of AI tools

I used an AI assistant for research and for accelerating the mechanical parts of this build:
scaffolding the Spring Boot and Vite projects, boilerplate (DTOs, controllers, CSS), and looking up
specifics I would otherwise have had to search for — the Cognito user-migration Lambda trigger as
the answer to no-forced-password-reset, and current Anthropic SDK usage for the structured-output
call.

The parts that are mine and that I can defend line by line: the two-phase design and the decision to
keep exclusion out of the score, the choice of five factors and the weights on them, the piecewise
cost-efficiency curve and the volume cap, the seniority and headcount profile-switching rule, the
tiebreak ordering, the binding-constraint output, the dataset and its deliberate spread, and the
architecture and migration reasoning in `docs/`.
