'use client';

// JobStatusPoller — M1.11
// Polls /api/v1/analysis/{jobId} until succeeded/failed, showing a live progress bar.

import { useEffect, useRef, useState } from 'react';
import { CheckCircle, XCircle, Loader2, Cpu } from 'lucide-react';
import { getJob, AnalysisJob } from '../lib/api';

interface Props {
  jobId: string;
  onComplete: (graphId: string) => void;
  onFailed: (message: string) => void;
}

const STAGE_LABELS: Record<string, string> = {
  fetching_contents:    'Fetching repository contents…',
  detecting_frameworks: 'Detecting frameworks & dependencies…',
  assembling_graph:     'Assembling architecture graph…',
  completed:            'Analysis complete!',
  failed:               'Analysis failed.',
};

export default function JobStatusPoller({ jobId, onComplete, onFailed }: Props) {
  const [job, setJob] = useState<AnalysisJob | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    let active = true;

    async function poll() {
      try {
        const j = await getJob(jobId);
        if (!active) return;
        setJob(j);

        if (j.status === 'succeeded' && j.graphId) {
          clearInterval(intervalRef.current!);
          setTimeout(() => onComplete(j.graphId!), 600);
        } else if (j.status === 'failed') {
          clearInterval(intervalRef.current!);
          onFailed(j.errorMessage ?? 'Analysis failed.');
        }
      } catch {
        // network blip — keep polling
      }
    }

    poll();
    intervalRef.current = setInterval(poll, 2000);

    return () => {
      active = false;
      clearInterval(intervalRef.current!);
    };
  }, [jobId, onComplete, onFailed]);

  const progress = job?.progress ?? 0;
  const stage = job?.stage ?? 'queued';
  const stageLabel = STAGE_LABELS[stage] ?? stage;

  return (
    <div className="fade-in glass-card" style={{ padding: 32, maxWidth: 520, width: '100%', margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24 }}>
        <div style={{ width: 44, height: 44, borderRadius: 12, background: 'var(--accent-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {job?.status === 'succeeded'
            ? <CheckCircle size={22} color="var(--success)" />
            : job?.status === 'failed'
            ? <XCircle size={22} color="var(--danger)" />
            : <Cpu size={22} color="var(--accent)" />}
        </div>
        <div>
          <p style={{ margin: 0, fontWeight: 700, fontSize: 16, color: 'var(--text-primary)' }}>
            Analyzing Repository
          </p>
          <p style={{ margin: '2px 0 0', fontSize: 13, color: 'var(--text-secondary)' }}>
            Job ID: <code style={{ fontSize: 11, color: 'var(--text-muted)' }}>{jobId.slice(0, 8)}…</code>
          </p>
        </div>
      </div>

      {/* Progress bar */}
      <div className="progress-bar" style={{ marginBottom: 12 }}>
        <div className="progress-bar-fill" style={{ width: `${progress}%` }} />
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {job?.status === 'running' && <Loader2 size={14} color="var(--accent)" style={{ animation: 'spin 1s linear infinite' }} />}
          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{stageLabel}</span>
        </div>
        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent)' }}>{progress}%</span>
      </div>

      {/* Stage timeline */}
      <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {[
          { key: 'fetching_contents',    label: 'Fetch contents',    pct: 10 },
          { key: 'detecting_frameworks', label: 'Detect frameworks', pct: 40 },
          { key: 'assembling_graph',     label: 'Assemble graph',    pct: 80 },
          { key: 'completed',            label: 'Complete',          pct: 100 },
        ].map(s => {
          const done = progress >= s.pct;
          const active = stage === s.key;
          return (
            <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{
                width: 8, height: 8, borderRadius: '50%',
                background: done ? 'var(--success)' : active ? 'var(--accent)' : 'var(--border-strong)',
                flexShrink: 0,
                boxShadow: active ? '0 0 8px var(--accent)' : 'none',
              }} />
              <span style={{ fontSize: 12, color: done ? 'var(--success)' : active ? 'var(--text-primary)' : 'var(--text-muted)' }}>
                {s.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
