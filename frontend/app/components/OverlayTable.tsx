'use client';

// OverlayTable — M1.13
// Full-screen overlay table for manually creating/deleting edges and viewing all nodes.

import { useState } from 'react';
import { X, Plus, Trash2 } from 'lucide-react';
import { GraphResponse, createEdge, deleteEdge } from '../lib/api';

interface Props {
  graph: GraphResponse;
  onClose: () => void;
  onChanged: () => void;
}

type Tab = 'nodes' | 'edges';

export default function OverlayTable({ graph, onClose, onChanged }: Props) {
  const [tab, setTab] = useState<Tab>('nodes');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // New edge form
  const [srcId, setSrcId] = useState(graph.nodes[0]?.id ?? '');
  const [tgtId, setTgtId] = useState(graph.nodes[1]?.id ?? graph.nodes[0]?.id ?? '');
  const [edgeType, setEdgeType] = useState('CALLS');

  async function handleCreateEdge() {
    if (!srcId || !tgtId || srcId === tgtId) { setError('Select two different nodes.'); return; }
    setSaving(true);
    setError(null);
    try {
      await createEdge(graph.id, { sourceNodeId: srcId, targetNodeId: tgtId, edgeType });
      onChanged();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to create edge.');
      setSaving(false);
    }
  }

  async function handleDeleteEdge(edgeId: string) {
    setSaving(true);
    setError(null);
    try {
      await deleteEdge(graph.id, edgeId);
      onChanged();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to delete edge.');
      setSaving(false);
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 50,
      background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div className="fade-in" style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--border)',
        borderRadius: 16,
        width: '90vw', maxWidth: 900, maxHeight: '85vh',
        display: 'flex', flexDirection: 'column',
        overflow: 'hidden',
      }}>
        {/* Header */}
        <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)' }}>Graph Editor</span>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }}>
            <X size={20} />
          </button>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', borderBottom: '1px solid var(--border)' }}>
          {(['nodes', 'edges'] as Tab[]).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              style={{
                padding: '10px 24px', border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 600,
                background: 'transparent', textTransform: 'capitalize',
                color: tab === t ? 'var(--accent)' : 'var(--text-muted)',
                borderBottom: tab === t ? '2px solid var(--accent)' : '2px solid transparent',
                transition: 'all 0.15s',
              }}
            >
              {t} ({t === 'nodes' ? graph.nodes.length : graph.edges.length})
            </button>
          ))}
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
          {tab === 'nodes' && (
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-muted)' }}>
                  {['Name', 'Type', 'Technology', 'Confidence', 'Source'].map(h => (
                    <th key={h} style={{ textAlign: 'left', padding: '0 12px 10px 0', borderBottom: '1px solid var(--border)' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {graph.nodes.map(n => (
                  <tr key={n.id} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ padding: '10px 12px 10px 0', fontSize: 13, color: 'var(--text-primary)', fontWeight: 600 }}>{n.name}</td>
                    <td style={{ padding: '10px 12px 10px 0' }}><span className={`badge badge-${typeBadge(n.type)}`}>{n.type}</span></td>
                    <td style={{ padding: '10px 12px 10px 0', fontSize: 12, color: 'var(--text-secondary)' }}>{n.technology ?? '—'}</td>
                    <td style={{ padding: '10px 12px 10px 0', fontSize: 12, color: 'var(--accent)' }}>{Math.round(n.confidence * 100)}%</td>
                    <td style={{ padding: '10px 0', fontSize: 11, color: 'var(--text-muted)', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.sourceFile ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {tab === 'edges' && (
            <>
              {/* Create edge */}
              <div style={{ marginBottom: 24, padding: 16, background: 'var(--bg-secondary)', borderRadius: 10, border: '1px solid var(--border)' }}>
                <p style={{ margin: '0 0 12px', fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>Create New Edge</p>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                  <div style={{ flex: 1, minWidth: 160 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>Source Node</label>
                    <select id="edge-src-select" className="select-field" style={{ width: '100%' }} value={srcId} onChange={e => setSrcId(e.target.value)}>
                      {graph.nodes.map(n => <option key={n.id} value={n.id}>{n.name}</option>)}
                    </select>
                  </div>
                  <div style={{ flex: 1, minWidth: 120 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>Edge Type</label>
                    <select id="edge-type-select" className="select-field" style={{ width: '100%' }} value={edgeType} onChange={e => setEdgeType(e.target.value)}>
                      {['CALLS', 'CONNECTS_TO', 'PUBLISHES_SUBSCRIBES', 'DEPENDS_ON'].map(t => <option key={t}>{t}</option>)}
                    </select>
                  </div>
                  <div style={{ flex: 1, minWidth: 160 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>Target Node</label>
                    <select id="edge-tgt-select" className="select-field" style={{ width: '100%' }} value={tgtId} onChange={e => setTgtId(e.target.value)}>
                      {graph.nodes.map(n => <option key={n.id} value={n.id}>{n.name}</option>)}
                    </select>
                  </div>
                  <button id="create-edge-btn" className="btn-primary" onClick={handleCreateEdge} disabled={saving} style={{ whiteSpace: 'nowrap' }}>
                    {saving ? <span className="spinner" /> : <Plus size={15} />}
                    Add Edge
                  </button>
                </div>
                {error && <p style={{ margin: '8px 0 0', fontSize: 12, color: 'var(--danger)' }}>{error}</p>}
              </div>

              {/* Edges table */}
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-muted)' }}>
                    {['Source', 'Type', 'Target', 'Confidence', ''].map((h, i) => (
                      <th key={i} style={{ textAlign: 'left', padding: '0 12px 10px 0', borderBottom: '1px solid var(--border)' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {graph.edges.map(e => {
                    const src = graph.nodes.find(n => n.id === e.sourceNodeId);
                    const tgt = graph.nodes.find(n => n.id === e.targetNodeId);
                    return (
                      <tr key={e.id} style={{ borderBottom: '1px solid var(--border)' }}>
                        <td style={{ padding: '10px 12px 10px 0', fontSize: 13, color: 'var(--text-primary)', fontWeight: 600 }}>{src?.name ?? '?'}</td>
                        <td style={{ padding: '10px 12px 10px 0' }}><span className="badge badge-neutral">{e.edgeType}</span></td>
                        <td style={{ padding: '10px 12px 10px 0', fontSize: 13, color: 'var(--text-primary)', fontWeight: 600 }}>{tgt?.name ?? '?'}</td>
                        <td style={{ padding: '10px 12px 10px 0', fontSize: 12, color: 'var(--text-muted)' }}>{Math.round(e.confidence * 100)}%</td>
                        <td style={{ padding: '10px 0' }}>
                          <button
                            onClick={() => handleDeleteEdge(e.id)}
                            disabled={saving}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--danger)', display: 'flex', alignItems: 'center' }}
                          >
                            <Trash2 size={14} />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                  {graph.edges.length === 0 && (
                    <tr><td colSpan={5} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)', fontSize: 13 }}>No edges yet</td></tr>
                  )}
                </tbody>
              </table>
            </>
          )}
        </div>
      </div>
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
