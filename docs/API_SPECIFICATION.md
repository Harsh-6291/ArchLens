# ArchLens — API Specification (Phase 1–2, MVP)

Base URL: `/api/v1`. Most endpoints require a valid session (`Authorization` via httpOnly cookie), but repo submission for **public** repos works with no session at all — GitHub login is only required when a submitted repo turns out to be private. Errors use a consistent envelope:

```json
{ "error": { "code": "STRING_CODE", "message": "human readable", "details": {} } }
```

---

## Auth

GitHub OAuth is **on-demand**, triggered from the repo-submission flow (see `POST /repos` below), not a mandatory entry point.

### `GET /auth/github/login?returnRepoUrl=...`
Redirects to GitHub OAuth authorize URL. `returnRepoUrl` is round-tripped through `state` so the callback knows which repo submission to retry.
- **Response**: 302 redirect.

### `GET /auth/github/callback?code=...&state=...`
Exchanges code for token, creates/updates `users` + `github_credentials`, issues session JWT (set as httpOnly cookie).
- **Response 200**: `{ "user": { "id", "githubUsername", "avatarUrl" }, "returnRepoUrl": "https://github.com/user/repo" }` — frontend uses `returnRepoUrl` to automatically retry the original `POST /repos` call.
- **Errors**: `400 invalid_state`, `401 github_auth_failed`

### `POST /auth/logout`
Invalidates session.
- **Response 204**

---

## Repositories

Repos are submitted **by URL**, not picked from a pre-fetched list — there is no "browse all my GitHub repos" step.

### `POST /repos`
Submits a GitHub repo URL for import. Tries the system GitHub identity first.
- **Body**: `{ "url": "https://github.com/user/repo" }`
- **Response 201** (public repo, fetch succeeded):
```json
{ "id": "uuid", "fullName": "user/repo", "defaultBranch": "main", "accessMethod": "public_system_token", "authorizedForTesting": false }
```
- **Response 401** (private/inaccessible repo, no session yet):
```json
{ "error": { "code": "requires_github_auth", "message": "This repository requires GitHub sign-in to access.", "details": { "loginUrl": "/auth/github/login?returnRepoUrl=https://github.com/user/repo" } } }
```
Frontend redirects the user to `details.loginUrl`; after OAuth completes, retries `POST /repos` with the same URL — this time the backend uses the now-available user token and creates the repo with `accessMethod: "user_oauth"`.
- **Errors**: `400 invalid_url` (not a valid GitHub repo URL), `404 repo_not_found` (public fetch got a real 404, not a private-repo 404 — backend distinguishes these when possible via GitHub's response), `429 github_rate_limited`

### `GET /repos`
Lists repos the signed-in user has claimed/owns in ArchLens (not a GitHub proxy — this is ArchLens's own registered repos, for returning users). Requires a session; anonymous public-repo analyses aren't listed here until claimed.
- **Query params**: `page`, `perPage`
- **Response 200**: `{ "repos": [ { "id": "uuid", "fullName": "user/repo", "defaultBranch": "main", "accessMethod": "public_system_token" } ], "hasMore": false }`

### `PATCH /repos/{id}`
Update authorization flag (required before any Phase 3+ testing) or claim an anonymous repo by setting the caller as owner.
- **Body**: `{ "authorizedForTesting": true }`
- **Response 200**: updated repo object.
- **Errors**: `403 not_owner` (if already claimed by someone else), `409 requires_ownership_claim` (if claiming and setting `authorizedForTesting` in the same call on an unclaimed repo without being signed in)

### `GET /repos/{id}/branches`
Proxies GitHub branches list, using whichever credential (`accessMethod`) is on record for this repo.
- **Response 200**: `{ "branches": [ { "name": "main", "commitSha": "abc123" } ] }`

### `GET /repos/{id}/commits?branch=main`
Proxies GitHub commits list for a branch, same credential rule as above.
- **Response 200**: `{ "commits": [ { "sha": "abc123", "message": "...", "authoredAt": "..." } ] }`

**Errors (all repo endpoints)**: `401 unauthorized`, `403 not_owner`, `404 repo_not_found`, `429 github_rate_limited` (include `retryAfterSeconds` in `details`).

---

## Analysis

### `POST /analysis`
Creates the `analysis_jobs` row and **publishes a message to the RabbitMQ `analysis-jobs` queue**; a separate worker process consumes it.
- **Body**: `{ "repositoryId": "uuid", "branch": "main", "commitSha": "abc123" }` (`commitSha` optional → defaults to branch HEAD)
- **Response 202**: `{ "jobId": "uuid", "status": "queued" }`
- **Errors**: `404 repo_not_found`, `409 analysis_already_running` (if an identical job is in flight)

### `GET /analysis/{jobId}`
Poll job status.
- **Response 200**:
```json
{
  "jobId": "uuid",
  "status": "running",
  "progress": { "stage": "detecting_frameworks", "percent": 40 },
  "graphId": null,
  "errorMessage": null
}
```
On success, `status: "succeeded"` and `graphId` is populated.

### `WS /analysis/{jobId}/stream` *(optional for MVP; polling is sufficient)*
Server pushes the same payload as above on each stage transition.

---

## Architecture Graph

### `GET /graphs/{graphId}`
Returns the fully assembled graph for rendering.
- **Response 200**:
```json
{
  "id": "uuid",
  "repositoryId": "uuid",
  "commitSha": "abc123",
  "nodes": [
    {
      "id": "uuid",
      "nodeKey": "service:order-service",
      "type": "service",
      "name": "Order Service",
      "technology": "Spring Boot 3.2",
      "sourceDirectory": "services/order",
      "confidence": 0.92,
      "position": { "x": 120, "y": 340 },
      "metadata": { "framework": "spring-boot", "language": "java" }
    }
  ],
  "edges": [
    { "id": "uuid", "fromNodeId": "uuid", "toNodeId": "uuid", "relationshipType": "calls", "protocol": "REST" }
  ]
}
```

### `GET /graphs/{graphId}/nodes/{nodeId}`
Inspector detail for a single node.
- **Response 200**: node object above, plus `sourceFiles: string[]`, `apis: [{ id, method, path }]` (once Phase 2 lands), `dependencies: [{ nodeId, name, type }]`.

### `PATCH /graphs/{graphId}/nodes/{nodeId}`
User edits to a node (position, or manual metadata override). Does not mutate the detected data — writes to the overlay table (see DATABASE_DESIGN.md §4).
- **Body**: `{ "position": { "x": 200, "y": 150 } }` and/or `{ "name": "Custom label" }`
- **Response 200**: updated node.

### `POST /graphs/{graphId}/nodes`
User manually adds a node not auto-detected.
- **Body**: `{ "type": "third_party_api", "name": "Stripe", "position": { "x": 500, "y": 100 } }`
- **Response 201**: created node.

### `DELETE /graphs/{graphId}/nodes/{nodeId}`
Removes a manually-added node (auto-detected nodes are hidden via overlay flag, not hard-deleted, to preserve re-analysis integrity).
- **Response 204**

### `POST /graphs/{graphId}/edges`
User manually connects two nodes.
- **Body**: `{ "fromNodeId": "uuid", "toNodeId": "uuid", "relationshipType": "calls", "protocol": "REST" }`
- **Response 201**: created edge.

### `DELETE /graphs/{graphId}/edges/{edgeId}`
- **Response 204**

**Errors (all graph endpoints)**: `403 not_owner`, `404 graph_not_found` / `node_not_found`

---

## API Definitions & Explorer (Phase 2)

### `GET /graphs/{graphId}/api-definitions`
Lists discovered + user-added APIs, grouped implicitly by `graphNodeId`.
- **Response 200**: `{ "apiDefinitions": [ { "id", "graphNodeId", "method", "path", "discovered": true, "authType": "bearer" } ] }`

### `POST /api-definitions`
User manually adds an API definition.
- **Body**: `{ "graphNodeId": "uuid", "method": "POST", "path": "/orders", "authType": "bearer" }`
- **Response 201**: created definition.

### `GET /api-definitions/{id}`
Full detail including inferred/stored request & response schema.

### `POST /api-definitions/{id}/execute`
Executes the API server-side (proxy) using supplied params/env, records a `request_executions` row.
- **Body**:
```json
{
  "headers": { "Authorization": "Bearer ..." },
  "queryParams": { "limit": "10" },
  "pathParams": { "id": "123" },
  "body": { "key": "value" },
  "environmentId": "uuid"
}
```
- **Response 200**:
```json
{
  "executionId": "uuid",
  "statusCode": 200,
  "latencyMs": 84,
  "responseHeaders": { "content-type": "application/json" },
  "responseBody": { "...": "..." },
  "error": null
}
```
- **Errors**: `400 invalid_params`, `502 upstream_unreachable` (target host didn't respond — include raw error in `details`), `504 upstream_timeout`

### `POST /api-definitions/{id}/saved-requests`
Save current request config as a named test case.
- **Body**: `{ "name": "Get order by id - happy path", "headers": {}, "queryParams": {}, "pathParams": {}, "body": {} }`
- **Response 201**: saved request object.

### `GET /api-definitions/{id}/saved-requests`
Lists saved requests for that API.

### `GET/POST /environments`
CRUD for named variable sets used across requests (name + key/value map; secret-flagged values stored encrypted).

---

## Conventions

- All list endpoints support `?page=&perPage=` and return `{ "items": [...], "total": n }` unless noted otherwise above (repo/branch/commit endpoints mirror GitHub's own pagination shape for simplicity).
- All timestamps are ISO-8601 UTC.
- All mutating endpoints require the caller to own the parent `repository` (checked via `repositories.user_id`).
- Rate limiting: `429` with `Retry-After` header on any endpoint that proxies GitHub, once GitHub's own rate limit is close to exhausted (surface `X-RateLimit-Remaining` from GitHub responses in job metadata for debugging).
