-- Migration V2: Analysis Jobs table

CREATE TABLE IF NOT EXISTS analysis_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    branch TEXT NOT NULL,
    commit_sha TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    stage TEXT,
    percent INTEGER DEFAULT 0,
    graph_id UUID,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    detector_results JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_repo_status ON analysis_jobs(repository_id, status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_repo_commit ON analysis_jobs(repository_id, commit_sha);
