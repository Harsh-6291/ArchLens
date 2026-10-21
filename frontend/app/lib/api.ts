// Central API client — all calls go through here.
// Base URL reads from NEXT_PUBLIC_API_URL env (defaults to localhost:8080 for dev).

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: 'include', // send httpOnly JWT cookie
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  });

  if (res.status === 401) throw new AuthError('Unauthorized');
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new ApiError(res.status, body || res.statusText);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export class AuthError extends Error { constructor(msg: string) { super(msg); this.name = 'AuthError'; } }
export class ApiError extends Error {
  constructor(public status: number, msg: string) { super(msg); this.name = 'ApiError'; }
}

// ─── Types ─────────────────────────────────────────────────────────────────

export interface RepoResponse {
  id: string;
  owner: string;
  name: string;
  fullName: string;
  accessMethod: 'public_system_token' | 'user_oauth';
  createdAt: string;
}

export interface RequiresAuthResponse {
  status: 'requires_github_auth';
  loginUrl: string;
}

export interface Branch { name: string; sha: string; }
export interface Commit { sha: string; message: string; author: string; date: string; }

export interface AnalysisJob {
  id: string;
  repositoryId: string;
  commitSha: string;
  status: 'queued' | 'running' | 'succeeded' | 'failed';
  stage: string | null;
  progress: number | null;
  graphId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GraphNode {
  id: string;
  nodeKey: string;
  name: string;
  type: string;
  technology: string | null;
  confidence: number;
  sourceFile: string | null;
  metadata: Record<string, unknown> | null;
}

export interface GraphEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  edgeType: string;
  confidence: number;
  metadata: Record<string, unknown> | null;
}

export interface GraphResponse {
  id: string;
  repositoryId: string;
  commitSha: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  createdAt: string;
}

// ─── Repo API ──────────────────────────────────────────────────────────────

export async function importRepo(url: string): Promise<RepoResponse | RequiresAuthResponse> {
  return apiFetch('/api/v1/repos', { method: 'POST', body: JSON.stringify({ url }) });
}

export async function getBranches(repoId: string): Promise<Branch[]> {
  return apiFetch(`/api/v1/repos/${repoId}/branches`);
}

export async function getCommits(repoId: string, branch: string): Promise<Commit[]> {
  return apiFetch(`/api/v1/repos/${repoId}/commits?branch=${encodeURIComponent(branch)}`);
}

// ─── Analysis API ──────────────────────────────────────────────────────────

export async function startAnalysis(repoId: string, commitSha: string): Promise<AnalysisJob> {
  return apiFetch('/api/v1/analysis', { method: 'POST', body: JSON.stringify({ repositoryId: repoId, commitSha }) });
}

export async function getJob(jobId: string): Promise<AnalysisJob> {
  return apiFetch(`/api/v1/analysis/${jobId}`);
}

// ─── Graph API ─────────────────────────────────────────────────────────────

export async function getGraph(graphId: string): Promise<GraphResponse> {
  return apiFetch(`/api/v1/graphs/${graphId}`);
}

// ─── Manual edit API (M1.13) ───────────────────────────────────────────────

export interface NodePatch {
  name?: string;
  type?: string;
  technology?: string;
  sourceFile?: string;
}
export interface EdgeCreate {
  sourceNodeId: string;
  targetNodeId: string;
  edgeType: string;
}

export async function patchNode(graphId: string, nodeId: string, patch: NodePatch): Promise<GraphNode> {
  return apiFetch(`/api/v1/graphs/${graphId}/nodes/${nodeId}`, { method: 'PATCH', body: JSON.stringify(patch) });
}

export async function deleteNode(graphId: string, nodeId: string): Promise<void> {
  return apiFetch(`/api/v1/graphs/${graphId}/nodes/${nodeId}`, { method: 'DELETE' });
}

export async function createEdge(graphId: string, edge: EdgeCreate): Promise<GraphEdge> {
  return apiFetch(`/api/v1/graphs/${graphId}/edges`, { method: 'POST', body: JSON.stringify(edge) });
}

export async function deleteEdge(graphId: string, edgeId: string): Promise<void> {
  return apiFetch(`/api/v1/graphs/${graphId}/edges/${edgeId}`, { method: 'DELETE' });
}

// ─── Phase 2: API Explorer & Environments ──────────────────────────────────

export interface ApiDefinitionDto {
  id: string;
  graphId: string;
  graphNodeId: string;
  httpMethod: string;
  path: string;
  controllerClassName: string | null;
  handlerMethodName: string | null;
  sourceFilePath: string | null;
  lineNumber: number | null;
  metadataJson: string | null;
}

export interface ApiDefinitionsListResponse {
  apiDefinitions: ApiDefinitionDto[];
}

export interface EnvironmentVariable {
  key: string;
  value: string;
  isSecret: boolean;
}

export interface EnvironmentResponse {
  id: string;
  name: string;
  variables: EnvironmentVariable[];
  createdAt: string;
  updatedAt: string;
}

export interface ExecuteApiRequest {
  pathParams?: Record<string, string>;
  queryParams?: Record<string, string>;
  headers?: Record<string, string>;
  body?: unknown;
  environmentId?: string;
}

export interface ExecuteApiResponse {
  executionId: string;
  statusCode: number;
  latencyMs: number;
  responseHeaders: Record<string, string>;
  responseBody: unknown;
  error: string | null;
}

export interface SavedRequestResponse {
  id: string;
  apiDefinitionId: string;
  name: string;
  description: string | null;
  pathParams: Record<string, string>;
  queryParams: Record<string, string>;
  headers: Record<string, string>;
  body: string | null;
  environmentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSavedRequestRequest {
  name: string;
  description?: string;
  pathParams?: Record<string, string>;
  queryParams?: Record<string, string>;
  headers?: Record<string, string>;
  body?: string;
  environmentId?: string;
}

export interface RequestExecutionResponse {
  id: string;
  apiDefinitionId: string;
  savedRequestId: string | null;
  statusCode: number;
  latencyMs: number;
  responseHeaders: string;
  responseBodyExcerpt: string | null;
  error: string | null;
  executedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export async function getApiDefinitionsForGraph(graphId: string): Promise<ApiDefinitionsListResponse> {
  return apiFetch(`/api/v1/graphs/${graphId}/api-definitions`);
}

export async function getApiDefinition(id: string): Promise<ApiDefinitionDto> {
  return apiFetch(`/api/v1/api-definitions/${id}`);
}

export async function executeApi(id: string, req: ExecuteApiRequest): Promise<ExecuteApiResponse> {
  return apiFetch(`/api/v1/api-definitions/${id}/execute`, { method: 'POST', body: JSON.stringify(req) });
}

export async function getExecutions(id: string, page = 0, size = 10): Promise<PageResponse<RequestExecutionResponse>> {
  return apiFetch(`/api/v1/api-definitions/${id}/executions?page=${page}&size=${size}`);
}

export async function getEnvironments(): Promise<EnvironmentResponse[]> {
  return apiFetch('/api/v1/environments');
}

export async function createEnvironment(name: string, variables: EnvironmentVariable[]): Promise<EnvironmentResponse> {
  return apiFetch('/api/v1/environments', { method: 'POST', body: JSON.stringify({ name, variables }) });
}

export async function getSavedRequests(apiDefinitionId: string): Promise<SavedRequestResponse[]> {
  return apiFetch(`/api/v1/api-definitions/${apiDefinitionId}/saved-requests`);
}

export async function createSavedRequest(apiDefinitionId: string, req: CreateSavedRequestRequest): Promise<SavedRequestResponse> {
  return apiFetch(`/api/v1/api-definitions/${apiDefinitionId}/saved-requests`, { method: 'POST', body: JSON.stringify(req) });
}

export async function deleteSavedRequest(apiDefinitionId: string, savedRequestId: string): Promise<void> {
  return apiFetch(`/api/v1/api-definitions/${apiDefinitionId}/saved-requests/${savedRequestId}`, { method: 'DELETE' });
}

// ─── Phase 3: Load Testing & Capacity ──────────────────────────────────────

export interface CreateLoadTestRequest {
  name: string;
  environmentId?: string;
  loadProfile?: 'RAMP_UP' | 'CONSTANT' | 'STRESS';
  virtualUsers?: number;
  durationSeconds?: number;
  rampUpSeconds?: number;
}

export interface LoadTestResponse {
  id: string;
  apiDefinitionId: string;
  environmentId: string | null;
  name: string;
  loadProfile: string;
  virtualUsers: number;
  durationSeconds: number;
  rampUpSeconds: number;
  status: string;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface LoadTestResultResponse {
  id: string;
  loadTestId: string;
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  requestsPerSecond: number;
  p50LatencyMs: number;
  p90LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  avgLatencyMs: number;
  errorRate: number;
  statusCodes: Record<string, number>;
  capacityThreshold: 'SAFE' | 'WARNING' | 'SATURATED' | 'FAILED';
  bottleneckDiagnosis: string | null;
  createdAt: string;
}

export async function createAndRunLoadTest(apiDefinitionId: string, req: CreateLoadTestRequest): Promise<LoadTestResponse> {
  return apiFetch(`/api/v1/api-definitions/${apiDefinitionId}/load-tests`, { method: 'POST', body: JSON.stringify(req) });
}

export async function getLoadTestsForApi(apiDefinitionId: string): Promise<LoadTestResponse[]> {
  return apiFetch(`/api/v1/api-definitions/${apiDefinitionId}/load-tests`);
}

export async function getLoadTest(id: string): Promise<LoadTestResponse> {
  return apiFetch(`/api/v1/load-tests/${id}`);
}

export async function getLoadTestResult(id: string): Promise<LoadTestResultResponse> {
  return apiFetch(`/api/v1/load-tests/${id}/results`);
}

// ─── Phase 4: Security Findings & AI Recommendations ───────────────────────

export interface SecurityFindingResponse {
  id: string;
  graphId: string;
  graphNodeId: string | null;
  evidenceId: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  title: string;
  category: string;
  description: string;
  remediation: string;
  sourceFile: string | null;
  lineNumber: number | null;
  createdAt: string;
}

export interface AiRecommendationResponse {
  id: string;
  graphId: string;
  problem: string;
  why: string;
  evidenceIds: string[];
  recommendation: string;
  impact: string;
  considerations: string;
  hallucinationVerified: boolean;
  createdAt: string;
}

export async function runSecurityScan(graphId: string): Promise<SecurityFindingResponse[]> {
  return apiFetch(`/api/v1/graphs/${graphId}/security-scan`, { method: 'POST' });
}

export async function getSecurityFindings(graphId: string): Promise<SecurityFindingResponse[]> {
  return apiFetch(`/api/v1/graphs/${graphId}/security-findings`);
}

export async function generateAiRecommendations(graphId: string): Promise<AiRecommendationResponse[]> {
  return apiFetch(`/api/v1/graphs/${graphId}/ai-recommendations`, { method: 'POST' });
}

export async function getAiRecommendations(graphId: string): Promise<AiRecommendationResponse[]> {
  return apiFetch(`/api/v1/graphs/${graphId}/ai-recommendations`);
}

// ─── Phase 5: Versioning & Comparison ──────────────────────────────────────

export interface ArchitectureVersionResponse {
  id: string;
  graphId: string;
  versionName: string;
  description: string | null;
  createdAt: string;
}

export interface ModifiedNodeDiff {
  nodeKey: string;
  oldName: string;
  newName: string;
  oldType: string;
  newType: string;
  oldTechnology: string | null;
  newTechnology: string | null;
}

export interface GraphDiffResponse {
  sourceGraphId: string;
  versionId: string;
  versionName: string;
  addedNodes: GraphNode[];
  removedNodes: GraphNode[];
  modifiedNodes: ModifiedNodeDiff[];
  addedEdges: GraphEdge[];
  removedEdges: GraphEdge[];
}

export async function createVersion(graphId: string, versionName: string, description?: string): Promise<ArchitectureVersionResponse> {
  return apiFetch(`/api/v1/graphs/${graphId}/versions`, { method: 'POST', body: JSON.stringify({ versionName, description }) });
}

export async function getVersions(graphId: string): Promise<ArchitectureVersionResponse[]> {
  return apiFetch(`/api/v1/graphs/${graphId}/versions`);
}

export async function compareVersion(graphId: string, targetVersionId: string): Promise<GraphDiffResponse> {
  return apiFetch(`/api/v1/graphs/${graphId}/compare?targetVersionId=${targetVersionId}`);
}

// ─── Phase 6: GitHub PR Webhook & Events ───────────────────────────────────

export interface GitHubPrEvent {
  id: string;
  repositoryId: string;
  prNumber: number;
  action: string;
  headSha: string;
  baseSha: string;
  headRef: string;
  baseRef: string;
  status: string;
  commentId: string | null;
  summaryMarkdown: string | null;
  createdAt: string;
}

export async function getRepoPrEvents(repositoryId: string): Promise<GitHubPrEvent[]> {
  return apiFetch(`/api/v1/repos/${repositoryId}/pr-events`);
}
