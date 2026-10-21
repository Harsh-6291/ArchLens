'use client';

import { useState, useEffect } from 'react';
import {
  X,
  History,
  GitCompare,
  Plus,
  ArrowRight,
  Clock,
  CheckCircle,
  AlertCircle,
  Layers,
} from 'lucide-react';
import {
  ArchitectureVersionResponse,
  GraphDiffResponse,
  createVersion,
  getVersions,
  compareVersion,
} from '../lib/api';

interface Props {
  graphId: string;
  onClose: () => void;
}

export default function VersionComparisonModal({ graphId, onClose }: Props) {
  const [versions, setVersions] = useState<ArchitectureVersionResponse[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<string>('');
  const [diff, setDiff] = useState<GraphDiffResponse | null>(null);
  const [loadingDiff, setLoadingDiff] = useState(false);

  // New version snapshot form
  const [newVersionName, setNewVersionName] = useState('');
  const [newVersionDesc, setNewVersionDesc] = useState('');
  const [creatingVersion, setCreatingVersion] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);

  useEffect(() => {
    loadVersions();
  }, [graphId]);

  function loadVersions() {
    getVersions(graphId)
      .then((vers) => {
        setVersions(vers);
        if (vers.length > 0 && !selectedVersionId) {
          setSelectedVersionId(vers[0].id);
          handleCompare(vers[0].id);
        }
      })
      .catch(() => {});
  }

  async function handleCompare(versionId: string) {
    if (!versionId) return;
    setSelectedVersionId(versionId);
    setLoadingDiff(true);
    setDiff(null);
    try {
      const res = await compareVersion(graphId, versionId);
      setDiff(res);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Comparison failed');
    } finally {
      setLoadingDiff(false);
    }
  }

  async function handleCreateSnapshot() {
    if (!newVersionName.trim()) return;
    setCreatingVersion(true);
    try {
      const v = await createVersion(graphId, newVersionName.trim(), newVersionDesc.trim() || undefined);
      setNewVersionName('');
      setNewVersionDesc('');
      setShowCreateForm(false);
      loadVersions();
      handleCompare(v.id);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to snapshot version');
    } finally {
      setCreatingVersion(false);
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
        {/* Header */}
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
              <GitCompare size={18} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)' }}>
                Architecture Versioning & Comparison
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                Compare current live architecture graph against recorded baseline snapshots
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              onClick={() => setShowCreateForm((s) => !s)}
              className="btn-primary"
              style={{ fontSize: 12, padding: '6px 14px' }}
            >
              <Plus size={13} /> Snapshot Current Version
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

        {/* Create form drawer */}
        {showCreateForm && (
          <div
            style={{
              padding: '14px 20px',
              background: 'var(--bg-secondary)',
              borderBottom: '1px solid var(--border)',
              display: 'flex',
              gap: 12,
              alignItems: 'flex-end',
            }}
          >
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                Version Name
              </label>
              <input
                className="input-field"
                placeholder="e.g. v1.2-release"
                value={newVersionName}
                onChange={(e) => setNewVersionName(e.target.value)}
              />
            </div>
            <div style={{ flex: 2 }}>
              <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>
                Description (Optional)
              </label>
              <input
                className="input-field"
                placeholder="Notes about architecture changes..."
                value={newVersionDesc}
                onChange={(e) => setNewVersionDesc(e.target.value)}
              />
            </div>
            <button
              onClick={handleCreateSnapshot}
              disabled={creatingVersion || !newVersionName.trim()}
              className="btn-primary"
              style={{ fontSize: 12, padding: '6px 16px' }}
            >
              {creatingVersion ? <span className="spinner" /> : 'Save Snapshot'}
            </button>
          </div>
        )}

        {/* Main Content Area */}
        <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
          {/* Left: Version Timeline List */}
          <div
            style={{
              width: 280,
              borderRight: '1px solid var(--border)',
              display: 'flex',
              flexDirection: 'column',
              background: 'var(--bg-secondary)',
            }}
          >
            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
              Saved Versions ({versions.length})
            </div>
            <div style={{ flex: 1, overflowY: 'auto', padding: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {versions.map((v) => (
                <div
                  key={v.id}
                  onClick={() => handleCompare(v.id)}
                  style={{
                    padding: '10px 12px',
                    borderRadius: 8,
                    cursor: 'pointer',
                    background: selectedVersionId === v.id ? 'var(--bg-card)' : 'transparent',
                    border: `1px solid ${selectedVersionId === v.id ? 'var(--accent)' : 'transparent'}`,
                    transition: 'all 0.15s ease',
                  }}
                >
                  <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>
                    {v.versionName}
                  </div>
                  {v.description && (
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                      {v.description}
                    </div>
                  )}
                  <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 4, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Clock size={10} /> {new Date(v.createdAt).toLocaleDateString()}
                  </div>
                </div>
              ))}
              {versions.length === 0 && (
                <div style={{ padding: 20, textAlign: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
                  No saved version snapshots yet. Click &quot;Snapshot Current Version&quot; above to capture a baseline.
                </div>
              )}
            </div>
          </div>

          {/* Right: Structural Diff Inspector */}
          <div style={{ flex: 1, overflowY: 'auto', padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
            {loadingDiff ? (
              <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span className="spinner" />
              </div>
            ) : diff ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', paddingBottom: 12 }}>
                  <div>
                    <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)' }}>
                      Diff: Live Graph vs {diff.versionName}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'rgba(16, 185, 129, 0.15)', color: '#10b981' }}>
                      +{diff.addedNodes.length} Added
                    </span>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'rgba(239, 68, 68, 0.15)', color: '#ef4444' }}>
                      -{diff.removedNodes.length} Removed
                    </span>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: 'rgba(245, 158, 11, 0.15)', color: '#f59e0b' }}>
                      ~{diff.modifiedNodes.length} Modified
                    </span>
                  </div>
                </div>

                {/* Added Nodes */}
                {diff.addedNodes.length > 0 && (
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#10b981', marginBottom: 8 }}>
                      + Added Architecture Nodes ({diff.addedNodes.length})
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {diff.addedNodes.map((n) => (
                        <div
                          key={n.id}
                          style={{
                            padding: '8px 12px',
                            background: 'rgba(16, 185, 129, 0.08)',
                            border: '1px solid rgba(16, 185, 129, 0.3)',
                            borderRadius: 6,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <div>
                            <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>{n.name}</span>
                            <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>({n.type})</span>
                          </div>
                          <code style={{ fontSize: 10, color: '#10b981' }}>{n.nodeKey}</code>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Removed Nodes */}
                {diff.removedNodes.length > 0 && (
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#ef4444', marginBottom: 8 }}>
                      - Removed Architecture Nodes ({diff.removedNodes.length})
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {diff.removedNodes.map((n) => (
                        <div
                          key={n.id}
                          style={{
                            padding: '8px 12px',
                            background: 'rgba(239, 68, 68, 0.08)',
                            border: '1px solid rgba(239, 68, 68, 0.3)',
                            borderRadius: 6,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                          }}
                        >
                          <div>
                            <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)', textDecoration: 'line-through' }}>{n.name}</span>
                            <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>({n.type})</span>
                          </div>
                          <code style={{ fontSize: 10, color: '#ef4444' }}>{n.nodeKey}</code>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Modified Nodes */}
                {diff.modifiedNodes.length > 0 && (
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#f59e0b', marginBottom: 8 }}>
                      ~ Modified Architecture Nodes ({diff.modifiedNodes.length})
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {diff.modifiedNodes.map((m) => (
                        <div
                          key={m.nodeKey}
                          style={{
                            padding: '10px 14px',
                            background: 'rgba(245, 158, 11, 0.08)',
                            border: '1px solid rgba(245, 158, 11, 0.3)',
                            borderRadius: 6,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 4,
                          }}
                        >
                          <code style={{ fontSize: 11, fontWeight: 700, color: '#f59e0b' }}>{m.nodeKey}</code>
                          <div style={{ fontSize: 12, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span>{m.oldName} ({m.oldType})</span>
                            <ArrowRight size={12} />
                            <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{m.newName} ({m.newType})</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {diff.addedNodes.length === 0 && diff.removedNodes.length === 0 && diff.modifiedNodes.length === 0 && (
                  <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
                    <CheckCircle size={28} color="#10b981" style={{ margin: '0 auto 12px' }} />
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Architecture Identical</div>
                    <div style={{ fontSize: 12, marginTop: 4 }}>No node or edge discrepancies detected between live graph and this snapshot.</div>
                  </div>
                )}
              </>
            ) : (
              <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
                Select a version on the left to inspect architectural differences.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
