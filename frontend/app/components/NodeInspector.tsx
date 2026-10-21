'use client';

// NodeInspector — M1.12
// Slide-in side panel showing detailed info about a clicked node.
// Allows inline editing of name/type/technology (M1.13 partial).

import { useState, useEffect } from 'react';
import { X, FileCode, ChevronRight, Save, Trash2, Globe, Play } from 'lucide-react';
import { GraphNode, patchNode, deleteNode, NodePatch, getApiDefinitionsForGraph, ApiDefinitionDto } from '../lib/api';
import ApiExplorerModal from './ApiExplorerModal';

interface Props {
  node: GraphNode;
  graphId: string;
  onClose: () => void;
  onUpdated: () => void;
}

const TYPE_OPTIONS = [
  'SERVICE', 'WEB_APPLICATION', 'SQL_DATABASE', 'NOSQL_DATABASE',
  'DATABASE', 'CACHE', 'MESSAGE_BROKER', 'LIBRARY', 'OTHER',
];

export default function NodeInspector({ node, graphId, onClose, onUpdated }: Props) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState(node.name);
  const [type, setType] = useState(node.type);
  const [technology, setTechnology] = useState(node.technology ?? '');
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Phase 2: API Definitions for this node
  const [apiDefinitions, setApiDefinitions] = useState<ApiDefinitionDto[]>([]);
  const [selectedApiDef, setSelectedApiDef] = useState<ApiDefinitionDto | null>(null);

  useEffect(() => {
    getApiDefinitionsForGraph(graphId)
      .then((res) => {
        const filtered = res.apiDefinitions.filter((a) => a.graphNodeId === node.id);
        setApiDefinitions(filtered);
      })
      .catch(() => {});
  }, [graphId, node.id]);

  const confidence = Math.round(node.confidence * 100);

  // Render metadata as collapsible key-value pairs
  const metaEntries = node.metadata
    ? Object.entries(node.metadata).filter(([k]) => k !== 'routes')
    : [];

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const patch: NodePatch = {};
      if (name !== node.name) patch.name = name;
      if (type !== node.type) patch.type = type;
      if (technology !== (node.technology ?? '')) patch.technology = technology || undefined;
      await patchNode(graphId, node.id, patch);
      onUpdated();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Save failed.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!confirmDelete) { setConfirmDelete(true); return; }
    setSaving(true);
    try {
      await deleteNode(graphId, node.id);
      onUpdated();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Delete failed.');
      setSaving(false);
    }
  }

  return (
    <div
      id="node-inspector-panel"
      className="panel-slide-in"
      style={{
        position: 'absolute', top: 0, right: 0, bottom: 0, width: 360,
        background: 'var(--bg-card)',
        borderLeft: '1px solid var(--border)',
        display: 'flex', flexDirection: 'column',
        zIndex: 20,
        overflowY: 'auto',
      }}
    >
      {/* Header */}
      <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-primary)' }}>Node Inspector</span>
        <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', display: 'flex', alignItems: 'center' }}>
          <X size={18} />
        </button>
      </div>

      <div style={{ padding: 20, flex: 1 }}>
        {/* Edit toggle */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
          <button className="btn-ghost" onClick={() => setEditing(e => !e)} style={{ fontSize: 12, padding: '5px 10px' }}>
            {editing ? 'Cancel' : '✏️ Edit'}
          </button>
        </div>

        {editing ? (
          /* Edit form */
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 5 }}>Name</label>
              <input id="node-name-input" className="input-field" value={name} onChange={e => setName(e.target.value)} />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 5 }}>Type</label>
              <select id="node-type-select" className="select-field" style={{ width: '100%' }} value={type} onChange={e => setType(e.target.value)}>
                {TYPE_OPTIONS.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 5 }}>Technology</label>
              <input id="node-tech-input" className="input-field" value={technology} onChange={e => setTechnology(e.target.value)} placeholder="e.g. Spring Boot, React…" />
            </div>
            {error && <p style={{ fontSize: 12, color: 'var(--danger)', margin: 0 }}>{error}</p>}
            <div style={{ display: 'flex', gap: 8 }}>
              <button id="node-save-btn" className="btn-primary" onClick={handleSave} disabled={saving} style={{ flex: 1, justifyContent: 'center' }}>
                {saving ? <span className="spinner" /> : <Save size={14} />}
                Save
              </button>
              <button
                id="node-delete-btn"
                onClick={handleDelete}
                disabled={saving}
                style={{
                  background: confirmDelete ? 'var(--danger)' : 'transparent',
                  border: `1px solid ${confirmDelete ? 'var(--danger)' : 'var(--border)'}`,
                  borderRadius: 8, padding: '8px 12px', cursor: 'pointer',
                  color: confirmDelete ? 'white' : 'var(--danger)',
                  fontSize: 13, display: 'flex', alignItems: 'center', gap: 6,
                  transition: 'all 0.2s',
                }}
              >
                <Trash2 size={14} />
                {confirmDelete ? 'Confirm' : 'Delete'}
              </button>
            </div>
          </div>
        ) : (
          /* View mode */
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <InfoRow label="Name" value={node.name} />
            <InfoRow label="Type" value={<span className={`badge badge-${typeBadge(node.type)}`}>{node.type}</span>} />
            {node.technology && <InfoRow label="Technology" value={node.technology} />}
            <InfoRow label="Confidence" value={`${confidence}%`} />
            {node.sourceFile && (
              <InfoRow label="Source File" value={
                <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: 'var(--accent)' }}>
                  <FileCode size={13} />{node.sourceFile}
                </span>
              } />
            )}
            <InfoRow label="Node Key" value={<code style={{ fontSize: 10, color: 'var(--text-muted)' }}>{node.nodeKey}</code>} />

            {/* API Endpoints (Phase 2) */}
            {apiDefinitions.length > 0 && (
              <div>
                <p style={{ margin: '0 0 8px', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                  Exposed APIs ({apiDefinitions.length})
                </p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {apiDefinitions.map((api) => (
                    <div
                      key={api.id}
                      onClick={() => setSelectedApiDef(api)}
                      style={{
                        padding: '8px 10px',
                        background: 'var(--bg-secondary)',
                        border: '1px solid var(--border)',
                        borderRadius: 6,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        transition: 'border-color 0.15s ease',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
                        <span
                          style={{
                            fontSize: 10,
                            fontWeight: 700,
                            padding: '2px 6px',
                            borderRadius: 4,
                            background:
                              api.httpMethod === 'GET'
                                ? '#059669'
                                : api.httpMethod === 'POST'
                                ? '#2563eb'
                                : api.httpMethod === 'DELETE'
                                ? '#dc2626'
                                : '#d97706',
                            color: '#fff',
                          }}
                        >
                          {api.httpMethod}
                        </span>
                        <span style={{ fontSize: 12, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {api.path}
                        </span>
                      </div>
                      <button
                        className="btn-ghost"
                        style={{ fontSize: 11, padding: '2px 6px', display: 'flex', alignItems: 'center', gap: 4 }}
                      >
                        <Play size={11} fill="currentColor" /> Test
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Metadata */}
            {metaEntries.length > 0 && (
              <div>
                <p style={{ margin: '0 0 8px', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Metadata</p>
                <div style={{ background: 'var(--bg-secondary)', borderRadius: 8, padding: 12 }}>
                  {metaEntries.map(([k, v]) => (
                    <div key={k} style={{ display: 'flex', gap: 8, marginBottom: 6, fontSize: 12 }}>
                      <span style={{ color: 'var(--text-muted)', minWidth: 90, flexShrink: 0 }}>{k}</span>
                      <ChevronRight size={12} color="var(--border-strong)" style={{ marginTop: 2 }} />
                      <span style={{ color: 'var(--text-secondary)', wordBreak: 'break-all' }}>
                        {typeof v === 'object' ? JSON.stringify(v).slice(0, 80) : String(v)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {selectedApiDef && (
        <ApiExplorerModal
          apiDef={selectedApiDef}
          onClose={() => setSelectedApiDef(null)}
        />
      )}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p style={{ margin: '0 0 3px', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</p>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{value}</div>
    </div>
  );
}

function typeBadge(type: string): string {
  if (type === 'SERVICE') return 'service';
  if (type.includes('DATABASE')) return 'database';
  if (type === 'CACHE') return 'cache';
  if (type === 'MESSAGE_BROKER') return 'broker';
  if (type === 'WEB_APPLICATION') return 'web';
  return 'neutral';
}
