'use client';

// Main page — orchestrates M1.11 → M1.12 → M1.13 flow:
//
// 1. Landing: AnalyzeForm (URL input → import → branch/commit picker)
// 2. Analyzing: JobStatusPoller (live progress bar, 2s polling)
// 3. Canvas: ArchCanvas (React Flow graph + NodeInspector + OverlayTable)

import { useState, useCallback } from 'react';
import AnalyzeForm from './components/AnalyzeForm';
import JobStatusPoller from './components/JobStatusPoller';
import ArchCanvas from './components/ArchCanvas';
import { getGraph, GraphResponse } from './lib/api';
import { AlertCircle, RotateCcw } from 'lucide-react';

type AppView = 'form' | 'polling' | 'graph' | 'error';

export default function HomePage() {
  const [view, setView] = useState<AppView>('form');
  const [jobId, setJobId] = useState<string | null>(null);
  const [graph, setGraph] = useState<GraphResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function handleJobStarted(id: string) {
    setJobId(id);
    setView('polling');
  }

  const handleAnalysisComplete = useCallback(async (graphId: string) => {
    setLoading(true);
    try {
      const g = await getGraph(graphId);
      setGraph(g);
      setView('graph');
    } catch (e: unknown) {
      setErrorMsg(e instanceof Error ? e.message : 'Failed to load graph.');
      setView('error');
    } finally {
      setLoading(false);
    }
  }, []);

  const handleAnalysisFailed = useCallback((msg: string) => {
    setErrorMsg(msg);
    setView('error');
  }, []);

  const handleGraphChanged = useCallback(async (graphId: string) => {
    try {
      const g = await getGraph(graphId);
      setGraph(g);
    } catch { /* ignore */ }
  }, []);

  function handleReset() {
    setView('form');
    setJobId(null);
    setGraph(null);
    setErrorMsg(null);
    setLoading(false);
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-primary)' }}>
      {/* Top nav bar — only shown when not in canvas view */}
      {view !== 'graph' && (
        <nav style={{
          height: 56, borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', padding: '0 24px',
          background: 'var(--bg-secondary)', flexShrink: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 28, height: 28, borderRadius: 7, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>
              🔭
            </div>
            <span style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)', letterSpacing: '-0.01em' }}>ArchLens</span>
          </div>
        </nav>
      )}

      {/* Canvas nav bar with "Analyze another" */}
      {view === 'graph' && (
        <nav style={{
          height: 52, borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px',
          background: 'var(--bg-secondary)', flexShrink: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 26, height: 26, borderRadius: 7, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12 }}>
              🔭
            </div>
            <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-primary)' }}>ArchLens</span>
            <span style={{ color: 'var(--border-strong)' }}>|</span>
            <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Architecture Graph</span>
          </div>
          <button className="btn-ghost" onClick={handleReset} style={{ fontSize: 12, padding: '5px 10px', display: 'flex', alignItems: 'center', gap: 6 }}>
            <RotateCcw size={13} />
            Analyze another repo
          </button>
        </nav>
      )}

      {/* Main content */}
      <main style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', overflow: 'hidden' }}>
        {view === 'form' && (
          <div style={{ padding: 24, width: '100%', maxWidth: 700 }}>
            <AnalyzeForm onJobStarted={handleJobStarted} />
          </div>
        )}

        {view === 'polling' && jobId && (
          <div style={{ padding: 24, width: '100%' }}>
            <JobStatusPoller
              jobId={jobId}
              onComplete={handleAnalysisComplete}
              onFailed={handleAnalysisFailed}
            />
            {loading && (
              <div style={{ textAlign: 'center', marginTop: 16, color: 'var(--text-muted)', fontSize: 13 }}>
                Loading graph…
              </div>
            )}
          </div>
        )}

        {view === 'graph' && graph && (
          <div style={{ position: 'absolute', inset: 0 }}>
            <ArchCanvas graph={graph} onGraphChanged={handleGraphChanged} />
          </div>
        )}

        {view === 'error' && (
          <div className="fade-in glass-card" style={{ padding: 32, maxWidth: 480, width: '100%', margin: 24, textAlign: 'center' }}>
            <AlertCircle size={40} color="var(--danger)" style={{ marginBottom: 16 }} />
            <p style={{ margin: '0 0 8px', fontWeight: 700, fontSize: 16, color: 'var(--text-primary)' }}>Something went wrong</p>
            <p style={{ margin: '0 0 24px', fontSize: 13, color: 'var(--text-secondary)' }}>{errorMsg}</p>
            <button className="btn-primary" onClick={handleReset} style={{ justifyContent: 'center', width: '100%' }}>
              <RotateCcw size={15} />
              Try again
            </button>
          </div>
        )}
      </main>
    </div>
  );
}
