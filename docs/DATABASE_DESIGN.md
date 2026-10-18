# ArchLens — Database Design

Primary store: **PostgreSQL**. The Architecture Graph is modeled relationally (adjacency-list style) rather than in a dedicated graph DB for MVP simplicity — it's small/medium-sized per repo and relational queries (joins, filters by type) are simpler to reason about and cache. Revisit only if graph queries become the bottleneck.

## 1. Entities (MVP scope: Phase 1–2; later-phase tables included for forward-compat but can be created in their own migration when that phase starts)

### users
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| github_id | bigint unique | |
| github_username | text | |
| email | text nullable | |
| avatar_url | text | |
| created_at | timestamptz | |

Created only when a user actually completes GitHub OAuth (the private-repo fallback path) — analyzing a public repo never requires a `users` row to exist.

### system_github_credential
| column | type | notes |
|---|---|---|
| id | uuid PK | singleton row — one system identity for the whole platform |
| access_token_encrypted | bytea | AES-GCM encrypted GitHub App/PAT token owned by ArchLens itself, not any end user |
| scopes | text[] | typically public-repo read only |
| expires_at | timestamptz nullable | |
| created_at | timestamptz | |
| rotated_at | timestamptz nullable | |

Used for every public-repo fetch. There is exactly one active row at a time; rotating the token inserts a new row and marks the old one inactive rather than mutating in place, for audit purposes.

### github_credentials
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| user_id | uuid FK → users.id | |
| access_token_encrypted | bytea | AES-GCM encrypted, never returned to frontend |
| scopes | text[] | |
| expires_at | timestamptz nullable | |
| created_at | timestamptz | |

Per-user credential, created on demand the first time that user authorizes access to a private repo — not at signup, since there is no separate signup step.

### repositories
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| user_id | uuid FK → users.id, **nullable** | null until a signed-in user claims/owns this repo record; public repos analyzed anonymously have no owner yet |
| github_repo_id | bigint | |
| full_name | text | e.g. `harsh/flowstate` |
| default_branch | text | |
| access_method | enum(`public_system_token`,`user_oauth`) | which credential was used to fetch this repo; determines which credential to use on re-analysis |
| authorized_for_testing | boolean default false | gate for load/security/resilience testing |
| created_at | timestamptz | |

A repo row can start with `access_method = public_system_token` and `user_id = null`, then later be claimed by a signed-in user (e.g. if they want to mark it `authorized_for_testing`, which requires an owner) — claiming just sets `user_id`. If the fetch attempt via the system token fails (private repo), no `repositories` row is created until the user completes OAuth and the fetch is retried with `access_method = user_oauth`.

### analysis_jobs
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| repository_id | uuid FK → repositories.id | |
| branch | text | |
| commit_sha | text | |
| status | enum(`queued`,`running`,`succeeded`,`failed`,`partial`) | |
| started_at | timestamptz nullable | |
| finished_at | timestamptz nullable | |
| error_message | text nullable | |
| detector_results | jsonb | which detectors ran, succeeded/failed — debugging aid |
| created_at | timestamptz | |

Unique index on `(repository_id, commit_sha)` — one canonical analysis per commit; re-running creates a new job but the graph is keyed by commit so history is preserved.

**Queue interaction**: creating an `analysis_jobs` row (`status: queued`) and publishing the corresponding RabbitMQ message happen in the same transaction/request — the message payload is just `{ jobId }`; the worker reads everything else it needs from this row, so the row (not the queue) remains the single source of truth for job state.

### architecture_graphs
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| repository_id | uuid FK | |
| analysis_job_id | uuid FK → analysis_jobs.id | |
| commit_sha | text | denormalized for fast lookup |
| created_at | timestamptz | |

### graph_nodes
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| graph_id | uuid FK → architecture_graphs.id | |
| node_key | text | stable id within the graph (e.g. `service:order-service`) for edge references and diffing across versions |
| type | enum | `service`,`microservice`,`monolith`,`worker`,`serverless_function`,`scheduled_job`,`api_gateway`,`load_balancer`,`reverse_proxy`,`cdn`,`sql_database`,`nosql_database`,`cache`,`object_storage`,`queue`,`kafka`,`rabbitmq`,`pubsub`,`third_party_api`,`payment_provider`,`email_provider`,`auth_provider`,`browser_client`,`mobile_client` |
| name | text | |
| technology | text nullable | e.g. `Spring Boot 3.2`, `PostgreSQL`, `Redis` |
| source_directory | text nullable | path within repo |
| source_files | text[] nullable | key files that led to detection |
| confidence | numeric(3,2) | 0–1, how sure the detector was |
| position_x | float nullable | canvas layout, user-adjustable |
| position_y | float nullable | |
| metadata | jsonb | free-form: config, detected env vars (redacted), framework version, etc. |
| created_at | timestamptz | |

### graph_edges
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| graph_id | uuid FK | |
| from_node_id | uuid FK → graph_nodes.id | |
| to_node_id | uuid FK → graph_nodes.id | |
| relationship_type | enum | `calls`,`depends_on`,`publishes_to`,`subscribes_to`,`reads_from`,`writes_to`,`routes_to` |
| protocol | text nullable | `REST`,`gRPC`,`GraphQL`,`AMQP`,`JDBC`,... |
| metadata | jsonb | e.g. inferred from which code location |
| created_at | timestamptz | |

### api_definitions  *(Phase 2)*
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| graph_node_id | uuid FK → graph_nodes.id | which service owns this API |
| method | enum | GET/POST/PUT/PATCH/DELETE |
| path | text | e.g. `/orders/{id}` |
| source_file | text nullable | |
| source_line | int nullable | |
| discovered | boolean | true if auto-discovered, false if user-added |
| auth_type | text nullable | |
| request_schema | jsonb nullable | |
| response_schema | jsonb nullable | |
| created_at | timestamptz | |

### saved_requests  *(Phase 2 — "saved test cases")*
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| api_definition_id | uuid FK | |
| user_id | uuid FK | |
| name | text | |
| headers | jsonb | |
| query_params | jsonb | |
| path_params | jsonb | |
| body | jsonb nullable | |
| environment_id | uuid FK → environments.id nullable | |
| created_at | timestamptz | |

### environments  *(Phase 2)*
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| user_id | uuid FK | |
| name | text | e.g. `local`, `staging` |
| variables | jsonb | key/value, secrets encrypted individually if flagged |

### request_executions  *(Phase 2)*
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| api_definition_id | uuid FK nullable | |
| saved_request_id | uuid FK nullable | |
| status_code | int nullable | |
| latency_ms | int nullable | |
| response_headers | jsonb nullable | |
| response_body_excerpt | text nullable | truncated |
| error | text nullable | |
| executed_at | timestamptz | |

### load_tests  *(Phase 3)*
| id, api_definition_id / graph_node_id, config jsonb (VUs, RPS, duration, ramp-up), status, started_at, finished_at |

### load_test_results  *(Phase 3)*
| id, load_test_id, throughput, avg_latency, p50, p90, p95, p99, max_latency, error_rate, status_code_breakdown jsonb, resource_utilization jsonb (cpu/mem/network/db/pool/queue_depth), measured_or_estimated enum |

### bottleneck_findings  *(Phase 3)*
| id, load_test_result_id, graph_node_id, title, description, evidence jsonb, recommendation text, severity |

### security_findings  *(Phase 4)*
| id, graph_node_id nullable, analysis_job_id, category enum(app/api/infra/dependency), title, severity, confidence, evidence jsonb, affected_file, affected_line, explanation, remediation, references text[], created_at |

### ai_recommendations  *(Phase 4)*
| id, architecture_graph_id, problem, why_it_matters, evidence jsonb, recommendation, expected_impact, implementation_considerations, created_at |

### ai_chat_messages  *(Phase 4)*
| id, user_id, repository_id, role enum(user/assistant), content text, cited_evidence jsonb, created_at |

### architecture_versions  *(Phase 5)*
| id, repository_id, architecture_graph_id, label (e.g. "v1", "post-cache-fix"), created_at |

### version_comparisons  *(Phase 5)*
| id, base_version_id, compare_version_id, diff jsonb (throughput/latency/p95/p99/error_rate/resource/capacity/security deltas), created_at |

## 2. Relationships Summary

- `users 0..1—N repositories` (nullable until claimed) `1—N analysis_jobs 1—1 architecture_graphs 1—N graph_nodes 1—N graph_edges`
- `system_github_credential` has no FK relationships — it's a standalone singleton referenced by application logic (by `repositories.access_method`), not by a foreign key
- `graph_nodes 1—N api_definitions 1—N saved_requests / request_executions`
- `architecture_graphs 1—N load_tests 1—N load_test_results 1—N bottleneck_findings`
- `analysis_jobs 1—N security_findings`
- `architecture_graphs 1—N ai_recommendations`, `repositories 1—N ai_chat_messages`
- `architecture_versions` reference a specific `architecture_graphs` row; `version_comparisons` reference two `architecture_versions`

## 3. Indexes

- `analysis_jobs (repository_id, commit_sha)` unique
- `graph_nodes (graph_id, type)` — canvas filters by type
- `graph_nodes (graph_id, node_key)` unique — stable reference for diffing/edges
- `graph_edges (graph_id, from_node_id)`, `graph_edges (graph_id, to_node_id)`
- `api_definitions (graph_node_id)`
- `security_findings (analysis_job_id, severity)`
- `load_test_results (load_test_id)`

## 4. Versioning Strategy

- Every `architecture_graphs` row is immutable once created and permanently tied to a `commit_sha`. Re-analyzing the same commit creates a new `analysis_jobs`/`architecture_graphs` pair rather than mutating the old one, so history is never lost.
- `node_key` (a stable logical id, not the DB `id`) is what lets the diffing engine (Phase 5) match "Order Service" across two graph versions even though the underlying UUIDs differ.
- User-made manual edits to a graph (moved node, added a node) are stored as an overlay: a `graph_node_overrides` table (id, graph_id, node_key, position_x, position_y, manual_metadata) applied on top of the detected graph at read time — keeps auto-detected data and user edits separable.

## 5. Architecture Graph Storage (explicit answer to source-spec requirement)

The graph is the join of `graph_nodes` + `graph_edges` for a given `architecture_graphs.id`. The API layer assembles this into a single JSON payload (`GET /graphs/{id}`) for the frontend, and this assembled JSON is what gets cached in Redis keyed by `graph_id`.

## 6. Test-Result Storage

`load_test_results` stores computed aggregates (percentiles, throughput) rather than every raw request sample, to keep the table small — if raw per-request traces are needed later for deep debugging, store them in object storage (e.g., a JSONL blob referenced by `load_test_results.raw_trace_url`) rather than in Postgres.

## 7. Security-Finding Storage

Findings are normalized into one shared table (`security_findings`) regardless of source scanner, so the AI layer and UI can treat "SQL injection found by static analysis" and "outdated dependency found by SCA tool" identically — same shape (title/severity/evidence/remediation), different `category`/source tag in metadata.
