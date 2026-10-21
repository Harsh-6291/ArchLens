'use client';

// GitHubPrModal — Phase 6: GitHub PR Integration
// Displays automated PR architecture and security delta summaries, webhook events, and GitHub comment status.

import { useEffect, useState } from 'react';
import { getRepoPrEvents, GitHubPrEvent } from '../lib/api';

interface Props {
  repositoryId: string;
  onClose: () => void;
}

export default function GitHubPrModal({ repositoryId, onClose }: Props) {
  const [events, setEvents] = useState<GitHubPrEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<GitHubPrEvent | null>(null);

  useEffect(() => {
    loadEvents();
  }, [repositoryId]);

  async function loadEvents() {
    setLoading(true);
    setError(null);
    try {
      const data = await getRepoPrEvents(repositoryId);
      setEvents(data);
      if (data.length > 0) {
        setSelectedEvent(data[0]);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load GitHub PR events');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 1000,
      background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(6px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24
    }}>
      <div style={{
        background: 'var(--bg-card)', border: '1px solid var(--border)',
        borderRadius: 12, width: '100%', maxWidth: 840, maxHeight: '85vh',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
        boxShadow: '0 20px 40px rgba(0,0,0,0.5)'
      }}>
        {/* Header */}
        <div style={{
          padding: '16px 24px', borderBottom: '1px solid var(--border)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center'
        }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 18, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 8 }}>
              <span>🐙</span> GitHub PR Architecture Governance
            </h2>
            <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--text-secondary)' }}>
              Automated PR checks, architecture deltas, and automated PR comment summaries
            </p>
          </div>
          <button
            onClick={onClose}
            className="btn-ghost"
            style={{ fontSize: 16, padding: '4px 8px' }}
          >
            ✕
          </button>
        </div>

        {/* Content Body */}
        <div style={{ display: 'flex', flex: 1, minHeight: 400, overflow: 'hidden' }}>
          {/* PR Events List sidebar */}
          <div style={{ width: 280, borderRight: '1px solid var(--border)', overflowY: 'auto', padding: 12 }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 8 }}>
              Recent PR Webhooks ({events.length})
            </div>

            {loading && <div style={{ fontSize: 13, color: 'var(--text-muted)', padding: 12 }}>Loading events...</div>}
            {error && <div style={{ fontSize: 12, color: '#ef4444', padding: 8 }}>{error}</div>}

            {!loading && events.length === 0 && (
              <div style={{ fontSize: 13, color: 'var(--text-muted)', padding: 12, lineHeight: 1.5 }}>
                No PR events recorded yet.<br />
                <span style={{ fontSize: 11 }}>Configure webhook at:<br /><code>POST /api/v1/webhooks/github/pr</code></span>
              </div>
            )}

            {events.map(ev => {
              const isSelected = selectedEvent?.id === ev.id;
              return (
                <div
                  key={ev.id}
                  onClick={() => setSelectedEvent(ev)}
                  style={{
                    padding: '10px 12px',
                    borderRadius: 8,
                    marginBottom: 6,
                    cursor: 'pointer',
                    background: isSelected ? 'rgba(99, 102, 241, 0.15)' : 'var(--bg-secondary)',
                    border: isSelected ? '1px solid #6366f1' : '1px solid transparent',
                    transition: 'all 0.15s ease'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>
                      PR #{ev.prNumber}
                    </span>
                    <span style={{
                      fontSize: 10,
                      padding: '2px 6px',
                      borderRadius: 4,
                      background: ev.status === 'commented' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(99, 102, 241, 0.2)',
                      color: ev.status === 'commented' ? '#10b981' : '#818cf8',
                      fontWeight: 600
                    }}>
                      {ev.status}
                    </span>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
                    {ev.action} · <code>{ev.headRef || ev.headSha?.slice(0, 7)}</code>
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 2 }}>
                    {new Date(ev.createdAt).toLocaleString()}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Event Details Viewer */}
          <div style={{ flex: 1, padding: 20, overflowY: 'auto' }}>
            {selectedEvent ? (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                  <div>
                    <h3 style={{ margin: 0, fontSize: 16, color: 'var(--text-primary)' }}>
                      Pull Request #{selectedEvent.prNumber}
                    </h3>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
                      Branches: <code>{selectedEvent.headRef || selectedEvent.headSha.slice(0, 7)}</code> ➔ <code>{selectedEvent.baseRef || selectedEvent.baseSha.slice(0, 7)}</code>
                    </div>
                  </div>
                  {selectedEvent.commentId && (
                    <div style={{ fontSize: 11, background: 'rgba(16, 185, 129, 0.15)', color: '#10b981', padding: '4px 8px', borderRadius: 6, border: '1px solid rgba(16, 185, 129, 0.3)' }}>
                      ✓ Posted comment ID: {selectedEvent.commentId}
                    </div>
                  )}
                </div>

                <div style={{
                  background: 'var(--bg-secondary)', border: '1px solid var(--border)',
                  borderRadius: 8, padding: 16, fontFamily: 'monospace', fontSize: 13,
                  lineHeight: 1.6, whiteSpace: 'pre-wrap', color: 'var(--text-primary)'
                }}>
                  {selectedEvent.summaryMarkdown || 'No summary generated.'}
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                Select a PR webhook event from the list to preview the automated impact summary.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
