# 🔍 ArchLens — Cloud-Native Architecture Intelligence & Governance

> **Understand, inspect, load-test, and govern your distributed software architecture directly from GitHub repositories in real time.**

ArchLens connects to any Git repository, builds a multi-tier architectural dependency graph using AST and static analysis, discovers APIs, runs concurrent load tests, identifies security bottlenecks, tracks version deltas, and automates Pull Request governance.

---

## ⚡ Core Capabilities

- **Automatic Architecture Extraction**:
  - **Tier 1 (Generic)**: Inspects package manifests (`package.json`, `pom.xml`, `build.gradle`, `requirements.txt`, `go.mod`, `Dockerfile`, `docker-compose.yml`).
  - **Tier 2 (Deep Framework AST)**: High-confidence parsing for Spring Boot (`@RestController`, `@Service`, `@Repository`), layered architecture inferencing, and config connection extraction.
- **Interactive Architecture Canvas**:
  - Real-time React Flow canvas with custom dark-themed nodes, confidence indicators, and interactive slide-in Node Inspector.
  - Manual graph overlay table for adding custom edges (`CALLS`, `CONNECTS_TO`, `PUBLISHES_SUBSCRIBES`) with automatic Redis cache invalidation.
- **API Discovery & Execution Proxy**:
  - Route discovery, query/body/header builder, environment manager with **AES-256 GCM** encrypted secrets, and execution history.
  - Visual request-flow animation pulsing through connected dependency edges on the architecture canvas.
- **Load Testing & Bottleneck Detection**:
  - Virtual-user load test runner with configurable duration, ramp-up, and real-time percentiles (`p50`, `p90`, `p95`, `p99`), throughput (RPS), and error rate.
  - Automated threshold categorizer (`SAFE`, `WARNING`, `SATURATED`) and bottleneck diagnosis.
- **Security Scanner & AI Guardrails**:
  - Proactive vulnerability scanning for unencrypted database connections and exposed actuator endpoints.
  - Grounded AI advisory recommendations with strict evidence citation verification (`verifyEvidenceCitations`).
- **Architecture Versioning & Comparison**:
  - Named graph snapshots with JSONB storage.
  - Graph diff engine matching nodes by `nodeKey` and edge signatures with visual added/removed/modified delta inspector.
- **GitHub PR Integration & Governance**:
  - Webhook receiver for GitHub PR events (`opened`, `synchronize`, `reopened`) with HMAC-SHA256 signature verification.
  - Automated PR comments reporting architecture impact, breaking changes, and security posture directly into GitHub PR conversations.

---

## 🛠️ Architecture & Technology Stack

| Layer | Technology |
|---|---|
| **Frontend** | Next.js 16 (App Router, Turbopack), React 19, TypeScript, TailwindCSS, `@xyflow/react` |
| **Backend** | Java 21, Spring Boot 3.3.4, Spring Data JPA, Spring AMQP, Flyway |
| **Data & Cache** | PostgreSQL 15, Redis 7 (architecture graph caching) |
| **Message Broker** | RabbitMQ 3.8+ (async analysis job workers & dead-letter queue) |
| **Security** | AES-256 GCM encryption at rest, HMAC-SHA256 webhooks, JWT HttpOnly sessions |

---

## 🚀 Getting Started

### Prerequisites
- **Docker** and **Docker Compose**
- **Java 21 JDK**
- **Node.js 20+** and **npm**

---

### Step 1: Start Infrastructure Containers

Start PostgreSQL, Redis, and RabbitMQ using Docker Compose:

```bash
docker-compose up -d
```

Containers and ports:
- **PostgreSQL**: `localhost:5434` (User: `archlens_user`, Database: `archlens_db`)
- **Redis**: `localhost:6380`
- **RabbitMQ**: `localhost:5672` (Management UI: `http://localhost:15672`, credentials: `archlens_rmq` / `archlens_rmq_pass`)

---

### Step 2: Run Backend Services

Navigate to `backend/` and start the Spring Boot application (Flyway automatically runs all database migrations `V1`–`V11`):

```bash
cd backend
./gradlew bootRun
```

To run all unit and integration tests (112+ tests):

```bash
./gradlew test
```

The backend starts at `http://localhost:8080`.

---

### Step 3: Run Frontend Web Application

Navigate to `frontend/` and start the Next.js development server:

```bash
cd frontend
npm install
npm run dev
```

Open your browser at `http://localhost:3000`.

To build the production bundle:

```bash
npm run build
```

---

## 📖 API Endpoints Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/repos` | Import public or private repository (GitHub token or OAuth fallback) |
| `GET` | `/api/v1/repos/{id}/branches` | List branches for repository |
| `GET` | `/api/v1/repos/{id}/commits` | List commits for branch |
| `POST` | `/api/v1/analysis` | Trigger background repository analysis job via RabbitMQ |
| `GET` | `/api/v1/analysis/{jobId}` | Poll progress and stage of analysis job |
| `GET` | `/api/v1/graphs/{id}` | Get assembled architecture graph (Redis cached) |
| `GET` | `/api/v1/graphs/{id}/api-definitions` | Get discovered REST API routes for graph nodes |
| `POST` | `/api/v1/api-definitions/{id}/execute` | Server-side execution proxy for target endpoint |
| `POST` | `/api/v1/api-definitions/{id}/load-tests` | Run virtual-user load test against endpoint |
| `POST` | `/api/v1/graphs/{id}/security-scan` | Run architecture security vulnerability scan |
| `POST` | `/api/v1/graphs/{id}/ai-recommendations` | Generate grounded architecture improvement recommendations |
| `POST` | `/api/v1/graphs/{id}/versions` | Create named architecture graph snapshot |
| `GET` | `/api/v1/graphs/{id}/compare?targetVersionId=...` | Diff active graph against a historical version |
| `POST` | `/api/v1/webhooks/github/pr` | GitHub Pull Request webhook receiver for automated PR comments |

---

## 🧪 Testing

Both backend and frontend have automated test coverage:

- **Backend**:
  ```bash
  cd backend
  ./gradlew test
  ```
  Runs AST detector tests, controller integration tests, RabbitMQ consumers, load testing runners, and webhook verifiers.
- **Frontend**:
  ```bash
  cd frontend
  npm run build
  ```
  Validates TypeScript types and Turbopack production compilation.

---

## 📄 License
MIT License. Created for cloud-native engineering and architecture governance.
