'use client';

// AnalyzeForm — M1.11
// URL input box, repo import, GitHub sign-in prompt, branch/commit picker, analyze trigger

import { useState } from 'react';
import { GitBranch, GitCommit, Search, AlertCircle, ExternalLink } from 'lucide-react';
import {
  importRepo, getBranches, getCommits, startAnalysis,
  Branch, Commit, RepoResponse, RequiresAuthResponse,
} from '../lib/api';

interface Props {
  onJobStarted: (jobId: string, repoId: string) => void;
}

type Step = 'url' | 'branch' | 'commit' | 'analyzing';

export default function AnalyzeForm({ onJobStarted }: Props) {
  const [url, setUrl] = useState('');
  const [step, setStep] = useState<Step>('url');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [repo, setRepo] = useState<RepoResponse | null>(null);
  const [authUrl, setAuthUrl] = useState<string | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [commits, setCommits] = useState<Commit[]>([]);
  const [selectedBranch, setSelectedBranch] = useState('');
  const [selectedCommit, setSelectedCommit] = useState('');

  const reset = () => {
    setStep('url');
    setError(null);
    setRepo(null);
    setAuthUrl(null);
    setBranches([]);
    setCommits([]);
    setSelectedBranch('');
    setSelectedCommit('');
  };

  // Step 1 — import repo
  async function handleImport() {
    if (!url.trim()) return;
    setLoading(true);
    setError(null);
    setAuthUrl(null);
    try {
      const result = await importRepo(url.trim());
      if ('status' in result && result.status === 'requires_github_auth') {
        const r = result as RequiresAuthResponse;
        setAuthUrl(r.loginUrl);
      } else {
        const r = result as RepoResponse;
        setRepo(r);
        // Load branches
        const brs = await getBranches(r.id);
        setBranches(brs);
        const defaultBranch = brs.find(b => b.name === 'main') ?? brs.find(b => b.name === 'master') ?? brs[0];
        if (defaultBranch) {
          setSelectedBranch(defaultBranch.name);
          await loadCommits(r.id, defaultBranch.name);
        }
        setStep('branch');
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to import repository.');
    } finally {
      setLoading(false);
    }
  }

  async function loadCommits(repoId: string, branch: string) {
    try {
      const cms = await getCommits(repoId, branch);
      setCommits(cms);
      if (cms[0]) setSelectedCommit(cms[0].sha);
    } catch {
      setCommits([]);
    }
  }

  async function handleBranchChange(branch: string) {
    setSelectedBranch(branch);
    setLoading(true);
    await loadCommits(repo!.id, branch);
    setLoading(false);
  }

  // Step 2 — start analysis
  async function handleAnalyze() {
    if (!repo || !selectedCommit) return;
    setLoading(true);
    setError(null);
    try {
      const job = await startAnalysis(repo.id, selectedCommit);
      onJobStarted(job.id, repo.id);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to start analysis.');
      setLoading(false);
    }
  }

  return (
    <div className="fade-in w-full max-w-2xl mx-auto">
      {/* Header */}
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 mb-4">
          <div style={{ width: 40, height: 40, borderRadius: 10, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Search size={20} color="white" />
          </div>
          <span style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)' }}>ArchLens</span>
        </div>
        <p style={{ color: 'var(--text-secondary)', fontSize: 15 }}>
          Discover the architecture of any GitHub repository
        </p>
      </div>

      <div className="glass-card p-6">
        {step === 'url' && (
          <>
            <label style={{ display: 'block', marginBottom: 6, fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              GitHub Repository URL
            </label>
            <div style={{ display: 'flex', gap: 10 }}>
              <input
                id="repo-url-input"
                className="input-field"
                placeholder="https://github.com/owner/repository"
                value={url}
                onChange={e => setUrl(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleImport()}
                disabled={loading}
              />
              <button
                id="analyze-btn"
                className="btn-primary"
                onClick={handleImport}
                disabled={loading || !url.trim()}
                style={{ whiteSpace: 'nowrap' }}
              >
                {loading ? <span className="spinner" /> : <Search size={16} />}
                {loading ? 'Loading…' : 'Import'}
              </button>
            </div>

            {/* GitHub Auth Required */}
            {authUrl && (
              <div className="fade-in" style={{ marginTop: 16, padding: '14px 16px', background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.3)', borderRadius: 10 }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                  <GitBranch size={18} color="var(--accent)" style={{ marginTop: 2, flexShrink: 0 }} />
                  <div>
                    <p style={{ margin: 0, fontWeight: 600, color: 'var(--text-primary)', fontSize: 14 }}>Private repository detected</p>
                    <p style={{ margin: '4px 0 12px', fontSize: 13, color: 'var(--text-secondary)' }}>
                      Sign in with GitHub to analyze private repositories.
                    </p>
                    <a
                      id="github-signin-btn"
                      href={authUrl}
                      className="btn-primary"
                      style={{ textDecoration: 'none', display: 'inline-flex' }}
                    >
                      <GitBranch size={16} />
                      Sign in with GitHub
                      <ExternalLink size={12} />
                    </a>
                  </div>
                </div>
              </div>
            )}

            {error && (
              <div style={{ marginTop: 12, display: 'flex', gap: 8, color: 'var(--danger)', fontSize: 13 }}>
                <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
                {error}
              </div>
            )}
          </>
        )}

        {(step === 'branch' || step === 'commit') && repo && (
          <div className="fade-in">
            {/* Repo info */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <GitBranch size={18} color="var(--accent)" />
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{repo.fullName}</span>
                <span className={`badge badge-${repo.accessMethod === 'user_oauth' ? 'service' : 'neutral'}`}>
                  {repo.accessMethod === 'user_oauth' ? 'Private' : 'Public'}
                </span>
              </div>
              <button className="btn-ghost" onClick={reset} style={{ fontSize: 12, padding: '5px 10px' }}>
                Change repo
              </button>
            </div>

            {/* Branch selector */}
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <GitBranch size={13} /> Branch
              </label>
              <select
                id="branch-select"
                className="select-field"
                style={{ width: '100%' }}
                value={selectedBranch}
                onChange={e => handleBranchChange(e.target.value)}
                disabled={loading}
              >
                {branches.map(b => (
                  <option key={b.name} value={b.name}>{b.name}</option>
                ))}
              </select>
            </div>

            {/* Commit selector */}
            {commits.length > 0 && (
              <div style={{ marginBottom: 20 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  <GitCommit size={13} /> Commit
                </label>
                <select
                  id="commit-select"
                  className="select-field"
                  style={{ width: '100%' }}
                  value={selectedCommit}
                  onChange={e => setSelectedCommit(e.target.value)}
                >
                  {commits.map(c => (
                    <option key={c.sha} value={c.sha}>
                      {c.sha.slice(0, 8)} — {c.message.slice(0, 60)}{c.message.length > 60 ? '…' : ''}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {error && (
              <div style={{ marginBottom: 12, display: 'flex', gap: 8, color: 'var(--danger)', fontSize: 13 }}>
                <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
                {error}
              </div>
            )}

            <button
              id="start-analysis-btn"
              className="btn-primary"
              onClick={handleAnalyze}
              disabled={loading || !selectedCommit}
              style={{ width: '100%', justifyContent: 'center' }}
            >
              {loading ? <span className="spinner" /> : <Search size={16} />}
              {loading ? 'Starting…' : 'Analyze Architecture'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
