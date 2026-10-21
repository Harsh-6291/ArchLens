'use client';

import { useState, useEffect } from 'react';
import {
  X,
  ShieldAlert,
  Sparkles,
  CheckCircle,
  AlertTriangle,
  FileCode,
  Check,
  RefreshCw,
  ExternalLink,
} from 'lucide-react';
import {
  SecurityFindingResponse,
  AiRecommendationResponse,
  runSecurityScan,
  getSecurityFindings,
  generateAiRecommendations,
  getAiRecommendations,
} from '../lib/api';

interface Props {
  graphId: string;
  onClose: () => void;
}

type TabMode = 'security' | 'ai';

export default function SecurityAndAiModal({ graphId, onClose }: Props) {
  const [tab, setTab] = useState<TabMode>('security');
  const [findings, setFindings] = useState<SecurityFindingResponse[]>([]);
  const [recommendations, setRecommendations] = useState<AiRecommendationResponse[]>([]);
  const [loadingScan, setLoadingScan] = useState(false);
  const [loadingAi, setLoadingAi] = useState(false);

  useEffect(() => {
    loadFindings();
    loadRecs();
  }, [graphId]);

  function loadFindings() {
    getSecurityFindings(graphId)
      .then(setFindings)
      .catch(() => {});
  }

  function loadRecs() {
    getAiRecommendations(graphId)
      .then(setRecommendations)
      .catch(() => {});
  }

  async function handleTriggerScan() {
    setLoadingScan(true);
    try {
      const res = await runSecurityScan(graphId);
      setFindings(res);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Security scan failed');
    } finally {
      setLoadingScan(false);
    }
  }

  async function handleTriggerAi() {
    setLoadingAi(true);
    try {
      const res = await generateAiRecommendations(graphId);
      setRecommendations(res);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'AI recommendation generation failed');
    } finally {
      setLoadingAi(false);
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
          height: '85vh',
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
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: 'rgba(99, 102, 241, 0.15)',
                color: 'var(--accent)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {tab === 'security' ? <ShieldAlert size={18} /> : <Sparkles size={18} />}
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)' }}>
                {tab === 'security' ? 'Security & Vulnerability Analysis' : 'Grounded AI Architectural Advisory'}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                {tab === 'security'
                  ? `${findings.length} findings identified in architecture`
                  : `${recommendations.length} evidence-backed architectural recommendations`}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {tab === 'security' ? (
              <button
                onClick={handleTriggerScan}
                disabled={loadingScan}
                className="btn-primary"
                style={{ fontSize: 12, padding: '6px 14px' }}
              >
                {loadingScan ? <span className="spinner" /> : <RefreshCw size={13} />}
                Run Security Scan
              </button>
            ) : (
              <button
                onClick={handleTriggerAi}
                disabled={loadingAi}
                className="btn-primary"
                style={{ fontSize: 12, padding: '6px 14px' }}
              >
                {loadingAi ? <span className="spinner" /> : <Sparkles size={13} />}
                Generate Recommendations
              </button>
            )}

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

        {/* Tab switch bar */}
        <div
          style={{
            display: 'flex',
            borderBottom: '1px solid var(--border)',
            background: 'var(--bg-secondary)',
            padding: '0 16px',
            gap: 12,
          }}
        >
          <button
            onClick={() => setTab('security')}
            style={{
              padding: '12px 14px',
              fontSize: 13,
              fontWeight: 600,
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              color: tab === 'security' ? 'var(--accent)' : 'var(--text-muted)',
              borderBottom: tab === 'security' ? '2px solid var(--accent)' : '2px solid transparent',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <ShieldAlert size={14} /> Security Findings ({findings.length})
          </button>
          <button
            onClick={() => setTab('ai')}
            style={{
              padding: '12px 14px',
              fontSize: 13,
              fontWeight: 600,
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              color: tab === 'ai' ? 'var(--accent)' : 'var(--text-muted)',
              borderBottom: tab === 'ai' ? '2px solid var(--accent)' : '2px solid transparent',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <Sparkles size={14} /> AI Recommendations ({recommendations.length})
          </button>
        </div>

        {/* Tab Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
          {tab === 'security' && (
            <>
              {findings.map((f) => (
                <div
                  key={f.id}
                  style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    padding: 16,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 10,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 700,
                          padding: '3px 8px',
                          borderRadius: 4,
                          background:
                            f.severity === 'CRITICAL' || f.severity === 'HIGH'
                              ? 'rgba(239, 68, 68, 0.2)'
                              : f.severity === 'MEDIUM'
                              ? 'rgba(245, 158, 11, 0.2)'
                              : 'rgba(59, 130, 246, 0.2)',
                          color:
                            f.severity === 'CRITICAL' || f.severity === 'HIGH'
                              ? '#ef4444'
                              : f.severity === 'MEDIUM'
                              ? '#f59e0b'
                              : '#3b82f6',
                          border: `1px solid ${
                            f.severity === 'CRITICAL' || f.severity === 'HIGH'
                              ? '#ef4444'
                              : f.severity === 'MEDIUM'
                              ? '#f59e0b'
                              : '#3b82f6'
                          }`,
                        }}
                      >
                        {f.severity}
                      </span>
                      <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-primary)' }}>
                        {f.title}
                      </span>
                    </div>
                    <code style={{ fontSize: 11, color: 'var(--text-muted)' }}>{f.evidenceId}</code>
                  </div>

                  <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                    {f.description}
                  </p>

                  <div
                    style={{
                      padding: '10px 12px',
                      background: 'var(--bg-card)',
                      borderRadius: 6,
                      border: '1px solid var(--border)',
                      fontSize: 12,
                    }}
                  >
                    <span style={{ fontWeight: 600, color: '#10b981' }}>Remediation: </span>
                    <span style={{ color: 'var(--text-primary)' }}>{f.remediation}</span>
                  </div>

                  {f.sourceFile && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--text-muted)' }}>
                      <FileCode size={13} />
                      <span>{f.sourceFile}{f.lineNumber ? `:${f.lineNumber}` : ''}</span>
                    </div>
                  )}
                </div>
              ))}
              {findings.length === 0 && (
                <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
                  No security findings yet. Click &quot;Run Security Scan&quot; to inspect this architecture.
                </div>
              )}
            </>
          )}

          {tab === 'ai' && (
            <>
              {recommendations.map((r) => (
                <div
                  key={r.id}
                  style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    padding: 16,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 12,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)' }}>
                      {r.problem}
                    </span>
                    {r.hallucinationVerified && (
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 600,
                          color: '#10b981',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                          background: 'rgba(16, 185, 129, 0.1)',
                          padding: '2px 8px',
                          borderRadius: 4,
                        }}
                      >
                        <Check size={12} /> Evidence Grounded
                      </span>
                    )}
                  </div>

                  <div style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                    <span style={{ fontWeight: 600, color: 'var(--text-muted)' }}>Why: </span>
                    {r.why}
                  </div>

                  <div
                    style={{
                      padding: 12,
                      background: 'var(--bg-card)',
                      borderRadius: 6,
                      border: '1px solid var(--border)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 6,
                    }}
                  >
                    <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--accent)' }}>
                      Recommendation
                    </span>
                    <span style={{ fontSize: 13, color: 'var(--text-primary)' }}>{r.recommendation}</span>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                    <div style={{ padding: 10, background: 'var(--bg-card)', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12 }}>
                      <span style={{ fontWeight: 600, color: '#10b981' }}>Impact: </span>
                      <span style={{ color: 'var(--text-secondary)' }}>{r.impact}</span>
                    </div>
                    <div style={{ padding: 10, background: 'var(--bg-card)', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12 }}>
                      <span style={{ fontWeight: 600, color: '#f59e0b' }}>Considerations: </span>
                      <span style={{ color: 'var(--text-secondary)' }}>{r.considerations}</span>
                    </div>
                  </div>

                  {r.evidenceIds && r.evidenceIds.length > 0 && (
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Cited Evidence:</span>
                      {r.evidenceIds.map((eid) => (
                        <code
                          key={eid}
                          style={{
                            fontSize: 10,
                            padding: '2px 6px',
                            background: 'rgba(99, 102, 241, 0.15)',
                            color: 'var(--accent)',
                            borderRadius: 4,
                          }}
                        >
                          {eid}
                        </code>
                      ))}
                    </div>
                  )}
                </div>
              ))}
              {recommendations.length === 0 && (
                <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
                  No architectural recommendations generated yet. Click &quot;Generate Recommendations&quot; to synthesize insights.
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
