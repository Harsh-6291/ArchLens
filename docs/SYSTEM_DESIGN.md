# ArchLens — System Design

## 1. High-Level Architecture

```
                         ┌───────────────────────────┐
                         │        Frontend (SPA)      │
                         │  Next.js + React + TS      │
                         │  React Flow canvas         │
                         └─────────────┬──────────────┘
                                       │ HTTPS / REST + WebSocket (live job status)
                         ┌─────────────▼──────────────┐
                         │        API Gateway layer     │
                         │  (Spring Boot controllers)   │
                         └─────────────┬──────────────┘
                                       │ publish job message
                         ┌─────────────▼──────────────┐
                         │         RabbitMQ            │
                         │  (analysis-jobs queue,      │
                         │   load-test-jobs queue,     │
                         │   dead-letter queues)        │
                         └─────────────┬──────────────┘
                                       │ consume
        ┌───────────────────┬─────────┼─────────────────┬────────────────────┐
        ▼                   ▼         ▼                 ▼                    ▼
 ┌─────────────┐   ┌────────────────┐ ┌───────────────┐ ┌────────────────┐ ┌────────────┐
 │ GitHub       │   │ Repo Analysis  │ │ API Explorer   │ │ Load Test       │ │ AI Reviewer │
 │ Integration  │   │ Worker         │ │ Service        │ │ Orchestrator    │ │ Service     │
 │ Service      │   │ (RabbitMQ      │ │ (sync, called  │ │ Worker          │ │ (Spring AI  │
 │ (system +    │   │  consumer)     │ │  directly from │ │ (RabbitMQ       │ │  abstraction)│
 │  user tokens)│   │                │ │  API tier)     │ │  consumer,      │ │             │
 │              │   │                │ │                │ │  wraps k6)      │ │             │
 └──────┬───────┘   └───────┬────────┘ └───────┬────────┘ └────────┬────────┘ └──────┬─────┘
        │                   │                  │                  │                  │
        └────────────┬──────┴───────┬──────────┴───────┬──────────┴─────────┬────────┘
                      ▼              ▼                  ▼                    ▼
              ┌────────────────────────────────────────────────────────────────┐
              │                    Architecture Graph Store                     │
              │            (PostgreSQL: relational graph tables)                │
              └──────────────┬───────────────────────────────┬──────────────────┘
                              ▼                               ▼
                     ┌───────────────┐               ┌────────────────┐
                     │  Redis cache   │               │  Object storage │
                     │  (job status,  │               │  (raw analysis  │
                     │  rate limits)  │               │  artifacts,     │
                     └───────────────┘               │  reports)        │
                                                       └────────────────┘
```

Analysis and load-test jobs are dispatched via **RabbitMQ**, not in-process async methods — the API tier publishes a message when a job is created and returns immediately; a separate worker (its own consumer process/thread pool) does the actual work and updates the job's status row in Postgres. The frontend polls job status or subscribes over WebSocket. This keeps the API responsive, survives API-process restarts without losing queued work, and gives each worker type (analysis vs. load-test) its own queue and scaling knob.

## 2. Services

| Service | Responsibility |
|---|---|
| **GitHub Integration Service** | Holds ArchLens's own system GitHub identity (App/PAT) for fetching public repos with no user login; falls back to per-user OAuth (Authorization Code flow, triggered on demand) for private repos; lists branches/commits; fetches repo contents/webhooks |
| **Repo Analysis Worker** | RabbitMQ consumer; reads repo at a commit, runs static-analysis detectors, builds Architecture Graph nodes/edges, persists them |
| **Architecture Graph Store** | Source of truth: nodes, edges, versions, metadata — everything else reads/writes here |
| **API Explorer Service** | Stores discovered + user-defined API definitions and executes HTTP requests on behalf of the user (server-side proxy so CORS/auth headers work) |
| **Load Test Orchestrator Worker** | RabbitMQ consumer; wraps k6 (or equivalent), manages test configs, runs, collects metrics, computes percentiles/capacity |
| **Bottleneck Detection Service** | Reads metrics + graph, produces evidence-linked bottleneck findings |
| **Security Analysis Service** | Runs static/dependency scanners, normalizes findings into the shared Finding model |
| **AI Reviewer Service** | Built on Spring AI (`ChatClient` + `ChatModel`, model-agnostic); a custom `Advisor` retrieves grounded context from graph/metrics/findings; structured recommendations produced via `BeanOutputConverter`; chat history via `ChatMemory` |
| **Versioning/Comparison Service** | Snapshots an architecture+test-result set as a version; diffs two versions |
| **Frontend** | Interactive canvas, inspectors, API Explorer UI, dashboards, chat UI |

For MVP (Phase 1–2), only **GitHub Integration**, **Repo Analysis**, **Architecture Graph Store**, and **API Explorer** need to exist as real services. Others can be stubbed/deferred per DEVELOPMENT_PLAN.md.

## 3. Data Flow (Phase 1–2)

1. User pastes a GitHub repo URL. No login required yet.
2. Backend attempts to fetch the repo using **ArchLens's own system GitHub identity** (a dedicated GitHub App/PAT, not tied to any user).
   - **Public repo** → fetch succeeds, proceed to step 3 immediately.
   - **Private repo / inaccessible** → backend returns a `requires_github_auth` response; frontend prompts "sign in with GitHub" → user completes OAuth (Authorization Code flow) scoped to repo read access → backend retries the fetch using the user's own token → proceed to step 3.
3. Backend lists branches/commits for the repo (via whichever credential succeeded) → user picks a commit (or latest).
4. Backend writes an `analysis_jobs` row (`status: queued`) and **publishes a message to the `analysis-jobs` RabbitMQ queue** → returns `jobId` immediately.
5. A separate **Repo Analysis Worker** process consumes the message, updates the job to `running`, fetches repo contents (via GitHub contents/tarball API, not a full git clone), and runs detectors:
   - manifest/dependency file detection (package.json, pom.xml, requirements.txt, Dockerfile, docker-compose.yml, k8s manifests) — broad, any-repo coverage
   - deep framework parsing for Spring Boot specifically (`@RestController`/`@RequestMapping`, layered architecture) — high-confidence, detailed nodes and API routes
   - config file scanning for DB/queue connection strings (redacted) to infer storage/messaging components
6. Worker builds graph nodes + edges, writes them to Postgres tagged with `repoId + commitSha`, updates the job to `succeeded`/`failed`.
7. Frontend polls/subscribes to job status; on completion, fetches the graph and renders it in React Flow.
8. In parallel, API discovery (Phase 2) parses route definitions into API nodes attached to the relevant service node.
9. User opens API Explorer, selects an endpoint, fills params, executes → backend proxies the HTTP call server-side (so the user doesn't fight CORS/auth) → returns status/latency/body to frontend.

## 4. APIs

See API_SPECIFICATION.md for full contracts. Grouped by:
- Auth (`/auth/github/*`)
- Repositories (`/repos`, `/repos/{id}/branches`, `/repos/{id}/commits`)
- Analysis (`/analysis`, `/analysis/{jobId}`)
- Architecture Graph (`/graphs/{id}`, `/graphs/{id}/nodes/{nodeId}`)
- API Explorer (`/api-definitions`, `/api-definitions/{id}/execute`)

## 5. Authentication & Authorization

- GitHub login is **on-demand, not mandatory**: public repos are analyzed using ArchLens's own system GitHub identity with no user login at all. GitHub OAuth (Authorization Code flow) is triggered only when a repo turns out to be private/inaccessible.
- Session via short-lived JWT (backend-issued) stored in an **httpOnly cookie** + refresh token; GitHub access token (user's or the system's) never exposed to frontend, always used server-side.
- Two distinct credential types: a single **system GitHub credential** (App/PAT) used for all public-repo fetches, and **per-user OAuth credentials** created only when a user authorizes access to a private repo.
- Authorization model: once a user has signed in (private-repo path), they can only see/act on their own repos and their own analysis jobs/graphs (row-level ownership check on every query). Repos analyzed anonymously via the public path are tracked by a session/browser identifier until claimed by a signed-in user.
- Before any load test or security scan can run against a target, the user must explicitly confirm ownership/authorization for that repo (a stored boolean flag set at registration time) — this is the enforcement point for the security boundary in the source spec, and it applies regardless of which credential path was used to fetch the repo.

## 6. Scalability

- Analysis and load-test jobs run on dedicated RabbitMQ consumer workers, decoupled from the stateless API tier — each queue (`analysis-jobs`, `load-test-jobs`) can scale its consumer count independently, and jobs survive an API-process restart since they live in the broker until acknowledged.
- Architecture Graph reads are the hottest path (every canvas load) — cache the assembled graph JSON per `repoId+commitSha` in Redis, invalidate on re-analysis.
- Load test execution is inherently bursty/expensive — run in isolated worker processes with resource caps, one test per target at a time to start.

## 7. Observability

- Structured logs (JSON) for every job with `jobId`, `repoId`, `commitSha`, stage, duration.
- Basic metrics: job success/failure counts, average analysis duration, GitHub API rate-limit remaining.
- Later phases add ingestion of the *target system's* runtime metrics (Prometheus-style) — kept separate from ArchLens's own operational metrics.

## 8. Failure Handling

- GitHub API failures (rate limit, 404, auth expiry) surface as a typed error on the job and a clear frontend message with a retry action.
- Partial analysis failure (one detector throws) should not fail the whole job — degrade gracefully and record which detectors succeeded/failed as job metadata.
- Load tests must have hard timeouts and resource ceilings so a misconfigured test can't take down the orchestrator itself.

## 9. Security

- All secrets (GitHub OAuth client secret, system GitHub App/PAT, encryption keys) via environment variables, never committed.
- GitHub tokens (system and per-user) encrypted at rest (e.g., AES-GCM with a key from a secrets manager/env).
- Any credentials discovered during repo analysis (connection strings, API keys in config files) must be redacted before storage/display — store only *that a database/queue was detected and its type*, never the literal secret.
- Server-side API Explorer proxy must strip/validate any header the user tries to inject that isn't relevant (avoid SSRF vectors — restrict target execution to hosts associated with the user's registered/authorized systems once that model exists; for MVP, warn clearly that execution happens server-side).

## 10. Deployment Architecture

- MVP: Spring Boot API + separate Spring Boot worker process(es) (or the same codebase run in "worker mode"), Next.js frontend, PostgreSQL, Redis, **RabbitMQ** — all run via docker-compose locally and on a small VM/managed platform for the portfolio demo.
- The API tier only publishes messages; it never runs analysis/load-test work inline. Workers are RabbitMQ consumers from day one (per decision to use a real broker starting in Phase 1), not a later upgrade from in-process `@Async`.
- Later: containerize each service independently; introduce Kafka only if a genuine event-streaming/multi-consumer need arises (not expected pre-Phase 6).

## 11. Explicit Non-Goals for MVP

- No Kubernetes deployment of ArchLens itself required for MVP.
- No multi-tenant billing/teams.
- No PR integration (Phase 6).
- No chaos/resilience testing until the load-testing foundation (Phase 3) is solid.
