# ArchLens — Product Requirements Document

## 1. Problem

Developers and teams building distributed systems have no single tool that connects:
- what the code actually is (repository structure, services, APIs)
- how the system is architected (components, protocols, dependencies)
- how it performs under load (throughput, latency, saturation)
- where it is insecure (vulnerabilities, misconfigurations)
- what an AI reasoning over *real evidence* (not guesses) recommends to fix it

Today these live in separate disconnected tools (draw.io for diagrams, Postman for APIs, k6/JMeter for load testing, Snyk for security, ChatGPT for advice) with no shared model. Nothing ties a security finding or a latency spike back to the exact file, commit, and architecture node that caused it.

**ArchLens** turns a GitHub repository into a live, testable, explorable system-design model — architecture, APIs, load behavior, security, and AI recommendations, all rooted in one **Architecture Graph** tied to real commits.

## 2. Target Users

- **Primary**: Backend/full-stack developers and students building portfolio-grade distributed systems who want to understand, test, and improve their own architecture.
- **Secondary**: Small teams doing architecture review before a release; interview candidates preparing system-design narratives with a real, working example.
- **Not targeted (explicitly out of scope)**: Pentesting arbitrary third-party sites. All load/security testing is scoped to repos the authenticated user owns or is authorized to test (see Security Boundary in SYSTEM_DESIGN.md).

## 3. Use Cases

1. Connect a GitHub repo → get an automatically discovered architecture diagram.
2. Explore a discovered API without writing a Postman collection by hand.
3. Load-test an API and see P50/P95/P99, error rate, and resource utilization.
4. Get an AI-generated, evidence-linked explanation of *why* an endpoint is slow.
5. Run a security scan and get findings traced to exact files/lines.
6. Make an architectural change, re-test, and see a before/after comparison.
7. Ask the AI free-form questions ("Can this handle 10k RPS?") and get answers grounded in the graph, metrics, and findings — not hallucinated.

## 4. User Journeys

**Journey A — First-time repo import**
Paste a GitHub URL (public or private) → if public, ArchLens fetches it immediately using its own system GitHub identity — no login required → if private, ArchLens prompts a one-time GitHub sign-in scoped to repo read access, then continues → select branch/commit → analysis runs → architecture graph appears in the interactive canvas → user inspects a node to see source files and detected technology.

**Journey B — API testing**
User opens API Explorer → sees discovered endpoints grouped by service → sends a request → sees status/latency/response → saves it as a test case.

**Journey C — Load test → bottleneck → fix**
User configures a load test on an endpoint → runs it → sees P99 spike and a bottleneck card with evidence ("connection pool utilization 96%") → asks the AI chat "why is this slow?" → AI cites the same evidence → user applies a suggested config change → re-runs → before/after comparison shows improvement.

**Journey D — Security pass**
User runs security analysis → findings list appears grouped by severity → each finding links to file/line → user asks AI to explain and remediate one finding.

## 5. Functional Requirements (MVP-relevant, mapped to phases)

| # | Requirement | Phase |
|---|---|---|
| FR1 | GitHub URL-based repo import: public repos via ArchLens's own system GitHub identity, private repos via on-demand user GitHub OAuth fallback | 1 |
| FR2 | Branch and commit selection | 1 |
| FR3 | Static analysis of repo to detect components (services, DBs, queues, frameworks) | 1 |
| FR4 | Persisted Architecture Graph (nodes, edges, metadata) versioned by commit | 1 |
| FR5 | Interactive canvas: pan/zoom/move/create/delete/connect nodes; inspector panel | 1 |
| FR6 | API discovery from code (routes, methods, params) | 2 |
| FR7 | API Explorer: build and execute requests against discovered APIs | 2 |
| FR8 | Saved test cases per API | 2 |
| FR9 | Load test configuration and execution (VUs, RPS, duration, ramp-up) | 3 |
| FR10 | Metrics collection and display (throughput, latency percentiles, error rates, resource use) | 3 |
| FR11 | Capacity analysis (safe/warning/saturation/failure thresholds), clearly labeled measured vs. estimated | 3 |
| FR12 | Bottleneck detection with evidence-linked explanations | 3 |
| FR13 | Security analysis (app, API, infra, dependency categories) with evidence and remediation | 4 |
| FR14 | AI Architecture Reviewer producing structured recommendations | 4 |
| FR15 | AI chat grounded in graph/metrics/findings | 4 |
| FR16 | Architecture versioning and before/after comparison | 5 |
| FR17 | GitHub PR integration for automated review | 6 |

## 6. Non-Functional Requirements

- **Evidence-first AI**: every AI claim about the user's specific system must be traceable to a retrieved fact (graph node, file, metric, finding). No project-specific hallucination.
- **Reproducibility**: every analysis is pinned to a repo+branch+commit so results can be regenerated and diffed.
- **Isolation of testing**: load/resilience/security tests only run against repos/environments the authenticated user has explicitly registered and confirmed ownership/authorization for.
- **Extensibility**: component types, AI providers, and analysis rules must be pluggable, not hardcoded to one stack.
- **Observability of the platform itself**: ArchLens should log and expose its own analysis pipeline health (so failures in repo analysis are debuggable).
- **Performance**: initial architecture discovery on a medium repo (~50k LOC) should complete in well under a few minutes for MVP acceptability; long jobs run async with progress status, not blocking requests.

## 7. MVP Definition

Phase 1 + Phase 2 as scoped in DEVELOPMENT_PLAN.md: GitHub-connected repo → architecture graph → interactive visualization → API discovery and execution. This alone is a demonstrable, portfolio-worthy product even before load testing/security/AI layers are added.

## 8. Future Features (Post-MVP)

Load/capacity testing, bottleneck detection, security analysis, AI reviewer and chat, architecture versioning/comparison, resilience/chaos testing, GitHub PR-integrated automated review.

## 9. Acceptance Criteria (MVP)

- A user can paste a public GitHub repo URL and see analysis begin without signing in.
- A user pasting a private repo URL is prompted to sign in with GitHub, and analysis proceeds afterward using their access.
- Selecting a repo+branch+commit triggers analysis and produces a non-empty Architecture Graph for both a generic Tier-1-detected repo (any manifest-recognized stack) and a Spring Boot repo (Tier 2, deep-parsed).
- The graph renders as an interactive canvas; every node has an inspector with source location and detected metadata.
- At least REST APIs are discovered and listed with method, path, and (when inferable) parameters.
- A discovered API can be executed from the API Explorer and shows status, latency, and response body.
- All analysis results are associated with the exact commit SHA analyzed.

## 10. Risks

- **Static analysis accuracy**: heuristic detection of frameworks/components across many languages is inherently imperfect — mitigated by the tiered approach: broad, lower-confidence generic detection for any repo, and one deeply-parsed, high-confidence stack (Spring Boot) to start. Confidence is always surfaced in the UI, never presented as certainty.
- **Scope creep**: the full spec is very large; strict phase discipline is required or nothing ships.
- **AI hallucination**: mitigated by mandatory evidence-citation in AI outputs and refusing to answer when no supporting data exists.
- **GitHub API rate limits**: must cache analysis results and avoid re-fetching unchanged commits.
- **Load-testing misuse**: mitigated by ownership/authorization confirmation gate before any test target can be registered.

## 11. Success Metrics (portfolio context, not commercial)

- End-to-end demo: import a real repo → see accurate architecture graph → execute a real API call, in under a few minutes, live.
- At least 2 different tech stacks correctly analyzed.
- Clean before/after comparison demo once Phase 5 lands.
