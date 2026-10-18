# ArchLens — Development Plan

Rule for the coding agent (Antigravity or otherwise): **implement one milestone at a time, in order, with tests before moving to the next.** Do not jump ahead to Phase 3+ work while Phase 1 milestones are incomplete. After each milestone, stop and confirm it runs end-to-end before continuing.

## Phase 0 — Project Scaffolding
- **M0.1**: Initialize monorepo structure: `/backend` (Spring Boot, Java 21, Gradle/Maven), `/frontend` (Next.js + TypeScript + Tailwind), `/docs` (this folder), `docker-compose.yml` (Postgres + Redis + **RabbitMQ**).
  - Test: `docker-compose up` brings up Postgres/Redis/RabbitMQ (management UI reachable); backend health endpoint `GET /actuator/health` returns 200; frontend dev server renders a placeholder page.
- **M0.2**: Configure Flyway/Liquibase for migrations; create initial migration for `users`, `system_github_credential`, `github_credentials`, `repositories` tables.
  - Test: migration runs clean on a fresh DB.
- **M0.3**: Configure Spring AMQP (`spring-boot-starter-amqp`); declare the `analysis-jobs` queue (plus a dead-letter queue) and a basic producer/consumer wiring smoke test.
  - Test: publishing a test message from the API context is received by a consumer in the same run; message survives a RabbitMQ container restart (durable queue).
- **M0.4**: Provision the system GitHub identity — a GitHub App or PAT owned by ArchLens (manual setup outside the codebase), store its encrypted token via a seed/admin script into `system_github_credential`.
  - Test: a smoke script calls the GitHub API using the stored system token and successfully fetches a known public repo's metadata.

## Phase 1 — URL-Based Repo Import, Architecture Discovery, Visualization

- **M1.1**: `POST /repos` — public-repo path only for this milestone: accept a GitHub URL, parse owner/repo, fetch repo metadata using the **system GitHub credential**, create the `repositories` row (`accessMethod: public_system_token`, `user_id: null`).
  - Test: fixture public repo URL → 201 with correct fields; malformed URL → 400; nonexistent repo → 404.
- **M1.2**: Private-repo fallback — detect a 404/403 from the system-token fetch that actually indicates a private repo, return `requires_github_auth` with a `loginUrl`; implement `GET /auth/github/login?returnRepoUrl=`, `GET /auth/github/callback` (GitHub OAuth, Authorization Code flow), session JWT issued as an httpOnly cookie; frontend retries `POST /repos` after callback, this time succeeding with `accessMethod: user_oauth` and a real `user_id`.
  - Test: integration test with a mocked GitHub API returning 404-for-private on the system token and 200 on a mocked user token; full round trip (submit → requires_github_auth → mock OAuth callback → retry succeeds) covered end-to-end.
- **M1.3**: Branch/commit listing (`GET /repos/{id}/branches`, `GET /repos/{id}/commits`) using whichever credential (`accessMethod`) is on record for the repo.
  - Test: mocked GitHub API client returns fixture branches/commits for both credential paths.
- **M1.4**: Analysis job infrastructure — `analysis_jobs` table, `POST /analysis` (writes the row + **publishes to the `analysis-jobs` RabbitMQ queue**), `GET /analysis/{jobId}` for polling.
  - Test: publishing a job message and having a stub consumer transition status queued → running → succeeded/failed; a message that fails processing lands on the dead-letter queue rather than being silently dropped.
- **M1.5**: Repo Analysis Worker — separate consumer (can be a distinct `@Component` with its own thread pool, or a distinct process/module if you want the isolation now) that fetches repo contents via GitHub contents/tarball API (not full clone) for a given commit, using the repo's recorded credential.
  - Test: given a fixture repo, contents are fetched and cached to a temp workspace; verify both system-token and user-token repos are fetched with the correct credential.
- **M1.6**: Detector (Tier 1 — generic, any repo): manifest/dependency detection — recognize `package.json`, `pom.xml`/`build.gradle`, `requirements.txt`, `go.mod`, `Dockerfile`, `docker-compose.yml`; lower-confidence, coarse-grained nodes (language + likely component type).
  - Test: unit tests per manifest type against fixture files, asserting correct component type + technology + confidence score.
- **M1.7**: Detector (Tier 2 — deep, Spring Boot specific): parse Spring `@RestController`/`@RequestMapping`/`@Service`/`@Repository` annotations to produce high-confidence service nodes with layered-architecture metadata.
  - Test: fixture Spring Boot source files → expected node list with correct confidence and metadata, higher than Tier 1 output for the same files.
- **M1.8**: Detector: infra/config scanning — detect DB/cache/queue usage from connection config (env var names, docker-compose service names) without capturing secret values.
  - Test: fixture `docker-compose.yml` with `postgres`/`redis` services → correct `sql_database`/`cache` nodes; secret values never appear in output.
- **M1.9**: Graph assembly — combine detector outputs into `graph_nodes`/`graph_edges`, persist under a new `architecture_graphs` row tied to `analysis_job_id` + `commit_sha`.
  - Test: end-to-end test on a fixture repo produces a graph with expected node count/types.
- **M1.10**: `GET /graphs/{graphId}` assembled JSON endpoint + Redis caching.
  - Test: first call misses cache and hits DB; second call served from cache; cache invalidated on new analysis of same repo.
- **M1.11**: Frontend — URL input box, "Analyze" trigger, inline "sign in with GitHub" prompt on `requires_github_auth`, branch/commit picker, job-status polling UI.
  - Test: Playwright happy-path test for both the public-repo flow (no login) and the private-repo flow (login prompted mid-flow) against a mocked backend.
- **M1.12**: Frontend — React Flow canvas rendering nodes/edges from `/graphs/{id}`; pan/zoom/move; node inspector panel showing name/type/technology/source files/confidence.
  - Test: component test rendering a fixture graph, verifying node count and inspector content on click.
- **M1.13**: Frontend/backend — manual node/edge create/delete/edit (`PATCH/POST/DELETE` graph endpoints) via overlay table.
  - Test: overlay applied correctly on top of detected graph on next fetch.

**Phase 1 exit criterion**: a public GitHub repo can be analyzed with zero login, and a private repo can be analyzed after an on-demand GitHub sign-in — both producing an explorable architecture graph. Test against at least one Spring Boot fixture repo (Tier 2) and one non-Spring-Boot fixture repo (Tier 1 only) to confirm the confidence distinction is visible.

## Phase 2 — API Discovery & Explorer

- **M2.1**: API route extraction as part of the Tier 2 Spring Boot detector (extend M1.7) — capture method + path + source location per `@RequestMapping`-family route, persist to `api_definitions` linked to the owning service node.
  - Test: fixture routes → expected `api_definitions` rows.
- **M2.2**: `GET /graphs/{graphId}/api-definitions`, `GET/POST /api-definitions/{id}`.
  - Test: endpoint returns correct grouping by node.
- **M2.3**: Environments CRUD (`/environments`) with encrypted secret-flagged values.
  - Test: secret values encrypted at rest, decrypted only on use.
- **M2.4**: Execution proxy — `POST /api-definitions/{id}/execute`, server-side HTTP call, `request_executions` persistence.
  - Test: mocked upstream server; execute returns correct status/latency/body; timeout and unreachable-host cases return proper error codes.
- **M2.5**: Saved requests (`POST/GET /api-definitions/{id}/saved-requests`).
  - Test: CRUD round-trip.
- **M2.6**: Frontend — API Explorer UI (endpoint list per service, request builder, response viewer, save-as-test-case).
  - Test: Playwright test executing a request against a mocked backend and asserting the response panel renders.
- **M2.7**: Frontend — request-flow visualization overlay on the canvas (highlight the call path for a selected API using `graph_edges`).
  - Test: component test on fixture graph + api definition.

**Phase 2 exit criterion**: discovered APIs can be listed, executed with real params, and results saved as reusable test cases — MVP is now demo-complete per PRD acceptance criteria.

## Phase 3 — Load Testing, Capacity, Bottlenecks *(outline — detail when Phase 2 is done)*
- Load test config UI + `load_tests`/`load_test_results` tables.
- Orchestrator wrapping k6 (generate a k6 script from an `api_definition` + config, run as subprocess/container, parse JSON summary output).
- Percentile/throughput/error-rate computation and storage.
- Capacity ramp test → threshold detection (safe/warning/saturation/failure), explicitly labeled measured vs. estimated.
- Bottleneck detection service correlating latency spikes with resource-utilization metadata.

## Phase 4 — Security Analysis & AI Layer *(outline)*
- Integrate/wrap open-source scanners (e.g., Semgrep for app/API patterns, a dependency-CVE checker) → normalize into `security_findings`.
- Add Spring AI dependency; configure a `ChatModel` (provider set via config — model-agnostic from day one, no separate abstraction needed since Spring AI provides it).
- Build a custom `Advisor` (`EvidenceRetrievalAdvisor`) that assembles graph + metrics + findings into grounded, evidence-ID-tagged context and injects it via the Spring AI `Advisor` chain.
- Define recommendation output as a Java record; use `BeanOutputConverter` to get structured `Problem → Why → Evidence → Recommendation → Impact → Considerations` output directly, no manual JSON parsing.
- Post-generation hallucination check: verify every cited evidence ID in the model's output exists in the context that was actually sent; strip/flag anything invented.
- AI chat endpoint + UI, using Spring AI's `ChatMemory` (backed by a custom `ChatMemoryRepository` over the `ai_chat_messages` table) for per-repo conversation history.
- See AI_ARCHITECTURE.md (to be created when this phase starts) for full detail.

## Phase 5 — Versioning & Comparison *(outline)*
- Snapshot current graph+results as a named `architecture_versions` entry.
- Diff engine comparing two versions by `node_key` matching; comparison UI (before/after metrics).

## Phase 6 — GitHub PR Integration *(outline)*
- Webhook receiver for PR events → incremental analysis of changed files → automated PR comment via GitHub API with architecture/security delta summary.

## Testing Strategy Applied Throughout
- Unit tests for every detector and every service method with business logic.
- Integration tests for every new endpoint (using Testcontainers for Postgres/Redis/**RabbitMQ**).
- E2E tests (Playwright) for each phase's exit-criterion user journey.
- No milestone is "done" without its test passing in CI.

## Decisions Made (resolved before Phase 1 implementation)
1. **Detection scope**: Tiered — generic manifest-based detection for any repo (Tier 1: package.json, pom.xml/build.gradle, requirements.txt, go.mod, Dockerfile, docker-compose.yml), plus deep, high-confidence Spring Boot-specific parsing (Tier 2: `@RestController`, layered architecture, full route extraction).
2. **Background job execution**: Real broker from day one — **RabbitMQ**, not in-process `@Async`. Separate consumer workers for `analysis-jobs` (and later `load-test-jobs`), with dead-letter queues for failed messages.
3. **Repo access model**: URL-only entry point, no repo browsing/listing upfront. Public repos fetched via ArchLens's own **system GitHub credential** (App/PAT) with zero user login. Private repos fall back to on-demand per-user GitHub OAuth, triggered only when the system-credential fetch fails as private/inaccessible. Content fetching itself uses the GitHub contents/tarball API (not git clone) regardless of which credential is used.
4. **Frontend auth storage**: httpOnly cookie for the session JWT (not Authorization header + memory/localStorage).
