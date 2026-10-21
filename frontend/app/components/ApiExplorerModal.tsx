'use client';

import { useState, useEffect } from 'react';
import {
  X,
  Play,
  Bookmark,
  History,
  Settings,
  ChevronDown,
  ChevronRight,
  Code2,
  Trash2,
  CheckCircle,
  AlertTriangle,
  Clock,
  Gauge,
  Activity,
  Zap,
} from 'lucide-react';
import {
  ApiDefinitionDto,
  EnvironmentResponse,
  SavedRequestResponse,
  RequestExecutionResponse,
  ExecuteApiResponse,
  LoadTestResponse,
  LoadTestResultResponse,
  getEnvironments,
  getSavedRequests,
  createSavedRequest,
  deleteSavedRequest,
  getExecutions,
  executeApi,
  createAndRunLoadTest,
  getLoadTestsForApi,
  getLoadTestResult,
} from '../lib/api';

interface Props {
  apiDef: ApiDefinitionDto;
  onClose: () => void;
}

type TabType = 'params' | 'headers' | 'body' | 'saved' | 'history' | 'loadtest';

export default function ApiExplorerModal({ apiDef, onClose }: Props) {
  const [activeTab, setActiveTab] = useState<TabType>('params');

  // Environments
  const [environments, setEnvironments] = useState<EnvironmentResponse[]>([]);
  const [selectedEnvId, setSelectedEnvId] = useState<string>('');

  // Phase 3: Load Testing State
  const [loadTests, setLoadTests] = useState<LoadTestResponse[]>([]);
  const [loadTestName, setLoadTestName] = useState('Capacity Ramp Test');
  const [loadProfile, setLoadProfile] = useState<'RAMP_UP' | 'CONSTANT' | 'STRESS'>('RAMP_UP');
  const [virtualUsers, setVirtualUsers] = useState<number>(10);
  const [durationSeconds, setDurationSeconds] = useState<number>(30);
  const [rampUpSeconds, setRampUpSeconds] = useState<number>(5);
  const [loadTestRunning, setLoadTestRunning] = useState(false);
  const [currentLoadResult, setCurrentLoadResult] = useState<LoadTestResultResponse | null>(null);


  // Request Builder State
  const [pathParams, setPathParams] = useState<Array<{ key: string; value: string }>>([]);
  const [queryParams, setQueryParams] = useState<Array<{ key: string; value: string }>>([{ key: '', value: '' }]);
  const [headers, setHeaders] = useState<Array<{ key: string; value: string }>>([
    { key: 'Content-Type', value: 'application/json' },
  ]);
  const [bodyText, setBodyText] = useState<string>('{\n  \n}');

  // Saved Requests
  const [savedRequests, setSavedRequests] = useState<SavedRequestResponse[]>([]);
  const [newSavedName, setNewSavedName] = useState('');
  const [saveLoading, setSaveLoading] = useState(false);

  // Execution History
  const [history, setHistory] = useState<RequestExecutionResponse[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  // Execution Response State
  const [executing, setExecuting] = useState(false);
  const [executionResult, setExecutionResult] = useState<ExecuteApiResponse | null>(null);
  const [executionError, setExecutionError] = useState<string | null>(null);

  // Extract route path params like {id}
  useEffect(() => {
    const matches = apiDef.path.match(/\{([^}]+)\}/g);
    if (matches) {
      setPathParams(matches.map((m) => ({ key: m.replace(/[{}]/g, ''), value: '' })));
    } else {
      setPathParams([]);
    }
  }, [apiDef.path]);

  // Load environments, saved requests, and history
  useEffect(() => {
    getEnvironments()
      .then((envs) => {
        setEnvironments(envs);
        if (envs.length > 0) setSelectedEnvId(envs[0].id);
      })
      .catch(() => {});

    loadSavedRequests();
    loadHistory();
    loadLoadTests();
  }, [apiDef.id]);

  function loadLoadTests() {
    getLoadTestsForApi(apiDef.id)
      .then((tests) => {
        setLoadTests(tests);
        if (tests.length > 0 && tests[0].status === 'COMPLETED') {
          getLoadTestResult(tests[0].id).then(setCurrentLoadResult).catch(() => {});
        }
      })
      .catch(() => {});
  }

  async function handleRunLoadTest() {
    setLoadTestRunning(true);
    setCurrentLoadResult(null);
    try {
      const run = await createAndRunLoadTest(apiDef.id, {
        name: loadTestName,
        loadProfile,
        virtualUsers,
        durationSeconds,
        rampUpSeconds,
        environmentId: selectedEnvId || undefined,
      });
      loadLoadTests();
      if (run.status === 'COMPLETED') {
        const res = await getLoadTestResult(run.id);
        setCurrentLoadResult(res);
      }
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Load test failed to execute');
    } finally {
      setLoadTestRunning(false);
    }
  }


  function loadSavedRequests() {
    getSavedRequests(apiDef.id)
      .then(setSavedRequests)
      .catch(() => {});
  }

  function loadHistory() {
    setHistoryLoading(true);
    getExecutions(apiDef.id, 0, 20)
      .then((res) => setHistory(res.content))
      .catch(() => {})
      .finally(() => setHistoryLoading(false));
  }

  async function handleExecute() {
    setExecuting(true);
    setExecutionError(null);
    setExecutionResult(null);

    const pathMap: Record<string, string> = {};
    pathParams.forEach((p) => {
      if (p.key.trim()) pathMap[p.key.trim()] = p.value;
    });

    const queryMap: Record<string, string> = {};
    queryParams.forEach((q) => {
      if (q.key.trim()) queryMap[q.key.trim()] = q.value;
    });

    const headerMap: Record<string, string> = {};
    headers.forEach((h) => {
      if (h.key.trim()) headerMap[h.key.trim()] = h.value;
    });

    let parsedBody: unknown = undefined;
    if (bodyText.trim() && (apiDef.httpMethod === 'POST' || apiDef.httpMethod === 'PUT' || apiDef.httpMethod === 'PATCH')) {
      try {
        parsedBody = JSON.parse(bodyText);
      } catch {
        parsedBody = bodyText;
      }
    }

    try {
      const res = await executeApi(apiDef.id, {
        pathParams: pathMap,
        queryParams: queryMap,
        headers: headerMap,
        body: parsedBody,
        environmentId: selectedEnvId || undefined,
      });
      setExecutionResult(res);
      loadHistory();
    } catch (err: unknown) {
      setExecutionError(err instanceof Error ? err.message : 'Execution failed');
      loadHistory();
    } finally {
      setExecuting(false);
    }
  }

  async function handleSaveRequest() {
    if (!newSavedName.trim()) return;
    setSaveLoading(true);

    const pathMap: Record<string, string> = {};
    pathParams.forEach((p) => {
      if (p.key.trim()) pathMap[p.key.trim()] = p.value;
    });

    const queryMap: Record<string, string> = {};
    queryParams.forEach((q) => {
      if (q.key.trim()) queryMap[q.key.trim()] = q.value;
    });

    const headerMap: Record<string, string> = {};
    headers.forEach((h) => {
      if (h.key.trim()) headerMap[h.key.trim()] = h.value;
    });

    try {
      await createSavedRequest(apiDef.id, {
        name: newSavedName.trim(),
        pathParams: pathMap,
        queryParams: queryMap,
        headers: headerMap,
        body: bodyText,
        environmentId: selectedEnvId || undefined,
      });
      setNewSavedName('');
      loadSavedRequests();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to save request');
    } finally {
      setSaveLoading(false);
    }
  }

  function handleApplySaved(saved: SavedRequestResponse) {
    if (saved.pathParams) {
      setPathParams(Object.entries(saved.pathParams).map(([k, v]) => ({ key: k, value: v })));
    }
    if (saved.queryParams) {
      setQueryParams(Object.entries(saved.queryParams).map(([k, v]) => ({ key: k, value: v })));
    }
    if (saved.headers) {
      setHeaders(Object.entries(saved.headers).map(([k, v]) => ({ key: k, value: v })));
    }
    if (saved.body) {
      setBodyText(saved.body);
    }
    if (saved.environmentId) {
      setSelectedEnvId(saved.environmentId);
    }
    setActiveTab('params');
  }

  async function handleDeleteSaved(savedId: string) {
    try {
      await deleteSavedRequest(apiDef.id, savedId);
      loadSavedRequests();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Delete failed');
    }
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 50,
        backgroundColor: 'rgba(5, 7, 13, 0.75)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 960,
          height: '88vh',
          background: 'var(--bg-card)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
          overflow: 'hidden',
        }}
      >
        {/* Top Header */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: 'var(--bg-secondary)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span
              style={{
                fontWeight: 700,
                fontSize: 12,
                padding: '4px 8px',
                borderRadius: 6,
                background:
                  apiDef.httpMethod === 'GET'
                    ? '#059669'
                    : apiDef.httpMethod === 'POST'
                    ? '#2563eb'
                    : apiDef.httpMethod === 'DELETE'
                    ? '#dc2626'
                    : '#d97706',
                color: '#fff',
              }}
            >
              {apiDef.httpMethod}
            </span>
            <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--text-primary)' }}>
              {apiDef.path}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {/* Environment Selector */}
            <select
              value={selectedEnvId}
              onChange={(e) => setSelectedEnvId(e.target.value)}
              className="select-field"
              style={{ fontSize: 12, padding: '4px 10px' }}
            >
              <option value="">No Environment (Direct)</option>
              {environments.map((env) => (
                <option key={env.id} value={env.id}>
                  {env.name}
                </option>
              ))}
            </select>

            <button
              onClick={handleExecute}
              disabled={executing}
              className="btn-primary"
              style={{ fontSize: 13, padding: '6px 14px' }}
            >
              {executing ? <span className="spinner" /> : <Play size={14} fill="currentColor" />}
              Send Request
            </button>

            <button
              onClick={onClose}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: 'var(--text-muted)',
              }}
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Main Grid: Request on Left, Response on Right */}
        <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
          {/* Left: Request Config Tabs */}
          <div
            style={{
              flex: 1,
              borderRight: '1px solid var(--border)',
              display: 'flex',
              flexDirection: 'column',
              minHeight: 0,
            }}
          >
            {/* Tabs */}
            <div
              style={{
                display: 'flex',
                borderBottom: '1px solid var(--border)',
                background: 'var(--bg-secondary)',
                padding: '0 8px',
              }}
            >
              {(['params', 'headers', 'body', 'saved', 'history', 'loadtest'] as TabType[]).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  style={{
                    padding: '10px 14px',
                    fontSize: 12,
                    fontWeight: 600,
                    textTransform: 'capitalize',
                    border: 'none',
                    background: 'none',
                    cursor: 'pointer',
                    color: activeTab === tab ? 'var(--accent)' : 'var(--text-muted)',
                    borderBottom: activeTab === tab ? '2px solid var(--accent)' : '2px solid transparent',
                  }}
                >
                  {tab === 'saved' ? `Saved (${savedRequests.length})` : tab === 'loadtest' ? '⚡ Load Testing' : tab}
                </button>
              ))}
            </div>

            {/* Tab Body */}
            <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
              {activeTab === 'params' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* Path Params */}
                  {pathParams.length > 0 && (
                    <div>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                        Path Variables
                      </span>
                      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
                        {pathParams.map((p, idx) => (
                          <div key={p.key} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            <input
                              disabled
                              value={`{${p.key}}`}
                              className="input-field"
                              style={{ width: 140, opacity: 0.7 }}
                            />
                            <input
                              placeholder="Value"
                              value={p.value}
                              onChange={(e) => {
                                const next = [...pathParams];
                                next[idx].value = e.target.value;
                                setPathParams(next);
                              }}
                              className="input-field"
                              style={{ flex: 1 }}
                            />
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Query Params */}
                  <div>
                    <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                      Query Parameters
                    </span>
                    <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {queryParams.map((q, idx) => (
                        <div key={idx} style={{ display: 'flex', gap: 8 }}>
                          <input
                            placeholder="Key"
                            value={q.key}
                            onChange={(e) => {
                              const next = [...queryParams];
                              next[idx].key = e.target.value;
                              setQueryParams(next);
                            }}
                            className="input-field"
                            style={{ flex: 1 }}
                          />
                          <input
                            placeholder="Value"
                            value={q.value}
                            onChange={(e) => {
                              const next = [...queryParams];
                              next[idx].value = e.target.value;
                              setQueryParams(next);
                            }}
                            className="input-field"
                            style={{ flex: 1 }}
                          />
                        </div>
                      ))}
                      <button
                        className="btn-ghost"
                        onClick={() => setQueryParams([...queryParams, { key: '', value: '' }])}
                        style={{ alignSelf: 'flex-start', fontSize: 11 }}
                      >
                        + Add Param
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'headers' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                    Headers
                  </span>
                  {headers.map((h, idx) => (
                    <div key={idx} style={{ display: 'flex', gap: 8 }}>
                      <input
                        placeholder="Header"
                        value={h.key}
                        onChange={(e) => {
                          const next = [...headers];
                          next[idx].key = e.target.value;
                          setHeaders(next);
                        }}
                        className="input-field"
                        style={{ flex: 1 }}
                      />
                      <input
                        placeholder="Value"
                        value={h.value}
                        onChange={(e) => {
                          const next = [...headers];
                          next[idx].value = e.target.value;
                          setHeaders(next);
                        }}
                        className="input-field"
                        style={{ flex: 1 }}
                      />
                    </div>
                  ))}
                  <button
                    className="btn-ghost"
                    onClick={() => setHeaders([...headers, { key: '', value: '' }])}
                    style={{ alignSelf: 'flex-start', fontSize: 11 }}
                  >
                    + Add Header
                  </button>
                </div>
              )}

              {activeTab === 'body' && (
                <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 8 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                    JSON Body
                  </span>
                  <textarea
                    value={bodyText}
                    onChange={(e) => setBodyText(e.target.value)}
                    className="input-field"
                    style={{
                      flex: 1,
                      minHeight: 220,
                      fontFamily: 'monospace',
                      fontSize: 12,
                      lineHeight: 1.5,
                      resize: 'none',
                    }}
                  />
                </div>
              )}

              {activeTab === 'saved' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* Save current config */}
                  <div style={{ display: 'flex', gap: 8 }}>
                    <input
                      placeholder="Name this request configuration..."
                      value={newSavedName}
                      onChange={(e) => setNewSavedName(e.target.value)}
                      className="input-field"
                      style={{ flex: 1 }}
                    />
                    <button
                      className="btn-secondary"
                      onClick={handleSaveRequest}
                      disabled={saveLoading || !newSavedName.trim()}
                      style={{ fontSize: 12, padding: '6px 12px' }}
                    >
                      <Bookmark size={13} />
                      Save
                    </button>
                  </div>

                  {/* List of saved */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {savedRequests.map((sr) => (
                      <div
                        key={sr.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '10px 14px',
                          background: 'var(--bg-secondary)',
                          border: '1px solid var(--border)',
                          borderRadius: 8,
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>
                            {sr.name}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                            {new Date(sr.createdAt).toLocaleDateString()}
                          </div>
                        </div>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button
                            className="btn-primary"
                            onClick={() => handleApplySaved(sr)}
                            style={{ fontSize: 11, padding: '4px 8px' }}
                          >
                            Load
                          </button>
                          <button
                            onClick={() => handleDeleteSaved(sr.id)}
                            style={{
                              background: 'none',
                              border: 'none',
                              cursor: 'pointer',
                              color: 'var(--danger)',
                            }}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    ))}
                    {savedRequests.length === 0 && (
                      <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        No saved requests yet for this API definition.
                      </p>
                    )}
                  </div>
                </div>
              )}

              {activeTab === 'history' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {historyLoading ? (
                    <div style={{ padding: 20, textAlign: 'center' }}>
                      <span className="spinner" />
                    </div>
                  ) : history.length === 0 ? (
                    <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                      No past executions recorded.
                    </p>
                  ) : (
                    history.map((h) => (
                      <div
                        key={h.id}
                        style={{
                          padding: '10px 12px',
                          background: 'var(--bg-secondary)',
                          border: '1px solid var(--border)',
                          borderRadius: 8,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <span
                            style={{
                              fontSize: 12,
                              fontWeight: 700,
                              color: (h.statusCode ?? 500) < 400 ? '#10b981' : '#ef4444',
                            }}
                          >
                            {h.statusCode ?? 'ERR'}
                          </span>
                          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                            {h.latencyMs}ms
                          </span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                          {new Date(h.executedAt).toLocaleTimeString()}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {activeTab === 'loadtest' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                  {/* Test Configuration */}
                  <div style={{ background: 'var(--bg-secondary)', padding: 14, borderRadius: 8, border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Zap size={14} color="var(--accent)" /> Configure Load Profile
                    </div>

                    <div>
                      <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                        Test Name
                      </label>
                      <input
                        className="input-field"
                        value={loadTestName}
                        onChange={(e) => setLoadTestName(e.target.value)}
                        placeholder="e.g. 50 VU Ramp Test"
                      />
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
                      <div>
                        <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                          Virtual Users ({virtualUsers})
                        </label>
                        <input
                          type="number"
                          min={1}
                          max={100}
                          className="input-field"
                          value={virtualUsers}
                          onChange={(e) => setVirtualUsers(parseInt(e.target.value) || 10)}
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                          Duration (Sec)
                        </label>
                        <input
                          type="number"
                          min={5}
                          max={120}
                          className="input-field"
                          value={durationSeconds}
                          onChange={(e) => setDurationSeconds(parseInt(e.target.value) || 30)}
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                          Ramp Up (Sec)
                        </label>
                        <input
                          type="number"
                          min={0}
                          max={60}
                          className="input-field"
                          value={rampUpSeconds}
                          onChange={(e) => setRampUpSeconds(parseInt(e.target.value) || 5)}
                        />
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
                      <button
                        onClick={handleRunLoadTest}
                        disabled={loadTestRunning}
                        className="btn-primary"
                        style={{ fontSize: 12, padding: '6px 16px' }}
                      >
                        {loadTestRunning ? <span className="spinner" /> : <Gauge size={13} />}
                        {loadTestRunning ? 'Running Load Test...' : 'Start Load Test'}
                      </button>
                    </div>
                  </div>

                  {/* Results & Threshold Dashboard */}
                  {currentLoadResult && (
                    <div style={{ background: 'var(--bg-secondary)', padding: 14, borderRadius: 8, border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: 14 }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
                          Latest Execution Metrics
                        </span>
                        <span
                          style={{
                            fontSize: 11,
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: 6,
                            background:
                              currentLoadResult.capacityThreshold === 'SAFE'
                                ? 'rgba(16, 185, 129, 0.2)'
                                : currentLoadResult.capacityThreshold === 'WARNING'
                                ? 'rgba(245, 158, 11, 0.2)'
                                : 'rgba(239, 68, 68, 0.2)',
                            color:
                              currentLoadResult.capacityThreshold === 'SAFE'
                                ? '#10b981'
                                : currentLoadResult.capacityThreshold === 'WARNING'
                                ? '#f59e0b'
                                : '#ef4444',
                            border: `1px solid ${
                              currentLoadResult.capacityThreshold === 'SAFE'
                                ? '#10b981'
                                : currentLoadResult.capacityThreshold === 'WARNING'
                                ? '#f59e0b'
                                : '#ef4444'
                            }`,
                          }}
                        >
                          {currentLoadResult.capacityThreshold} CAPACITY
                        </span>
                      </div>

                      {/* Stat Tiles */}
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                        <div style={{ background: 'var(--bg-card)', padding: 10, borderRadius: 6, border: '1px solid var(--border)' }}>
                          <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Throughput</div>
                          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                            {Math.round(currentLoadResult.requestsPerSecond)} req/s
                          </div>
                        </div>
                        <div style={{ background: 'var(--bg-card)', padding: 10, borderRadius: 6, border: '1px solid var(--border)' }}>
                          <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>p50 Latency</div>
                          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                            {currentLoadResult.p50LatencyMs} ms
                          </div>
                        </div>
                        <div style={{ background: 'var(--bg-card)', padding: 10, borderRadius: 6, border: '1px solid var(--border)' }}>
                          <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>p95 Latency</div>
                          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                            {currentLoadResult.p95LatencyMs} ms
                          </div>
                        </div>
                        <div style={{ background: 'var(--bg-card)', padding: 10, borderRadius: 6, border: '1px solid var(--border)' }}>
                          <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Error Rate</div>
                          <div style={{ fontSize: 14, fontWeight: 700, color: currentLoadResult.errorRate > 0 ? '#ef4444' : '#10b981', marginTop: 4 }}>
                            {(currentLoadResult.errorRate * 100).toFixed(1)}%
                          </div>
                        </div>
                      </div>

                      {/* Bottleneck Diagnosis */}
                      {currentLoadResult.bottleneckDiagnosis && (
                        <div style={{ padding: 10, background: 'var(--bg-card)', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12 }}>
                          <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Diagnosis: </span>
                          <span style={{ color: 'var(--text-secondary)' }}>{currentLoadResult.bottleneckDiagnosis}</span>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Past Load Runs */}
                  {loadTests.length > 0 && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                        Past Runs ({loadTests.length})
                      </span>
                      {loadTests.map((t) => (
                        <div
                          key={t.id}
                          onClick={() => getLoadTestResult(t.id).then(setCurrentLoadResult).catch(() => {})}
                          style={{
                            padding: '8px 12px',
                            background: 'var(--bg-secondary)',
                            border: '1px solid var(--border)',
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            cursor: 'pointer',
                            fontSize: 12,
                          }}
                        >
                          <div>
                            <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{t.name}</span>
                            <span style={{ color: 'var(--text-muted)', marginLeft: 8 }}>({t.virtualUsers} VUs)</span>
                          </div>
                          <span style={{ fontSize: 11, color: t.status === 'COMPLETED' ? '#10b981' : '#f59e0b' }}>
                            {t.status}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>


          {/* Right: Response Panel */}
          <div
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              minHeight: 0,
              background: 'var(--bg-secondary)',
            }}
          >
            {/* Header info */}
            <div
              style={{
                padding: '12px 16px',
                borderBottom: '1px solid var(--border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                Response
              </span>
              {executionResult && (
                <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: executionResult.statusCode < 400 ? '#10b981' : '#ef4444',
                    }}
                  >
                    Status: {executionResult.statusCode}
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    Time: {executionResult.latencyMs}ms
                  </span>
                </div>
              )}
            </div>

            {/* Response Viewer */}
            <div style={{ flex: 1, padding: 16, overflowY: 'auto' }}>
              {executing ? (
                <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <span className="spinner" />
                </div>
              ) : executionError ? (
                <div
                  style={{
                    padding: 16,
                    background: 'rgba(239, 68, 68, 0.1)',
                    border: '1px solid var(--danger)',
                    borderRadius: 8,
                    color: 'var(--danger)',
                    fontSize: 13,
                  }}
                >
                  <AlertTriangle size={16} style={{ marginBottom: 6 }} />
                  <div>{executionError}</div>
                </div>
              ) : executionResult ? (
                <pre
                  style={{
                    margin: 0,
                    fontFamily: 'monospace',
                    fontSize: 12,
                    color: 'var(--text-primary)',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                  }}
                >
                  {typeof executionResult.responseBody === 'object'
                    ? JSON.stringify(executionResult.responseBody, null, 2)
                    : String(executionResult.responseBody ?? '')}
                </pre>
              ) : (
                <div
                  style={{
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--text-muted)',
                    gap: 8,
                  }}
                >
                  <Code2 size={24} style={{ opacity: 0.5 }} />
                  <span style={{ fontSize: 13 }}>Click &quot;Send Request&quot; to execute this API endpoint</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
