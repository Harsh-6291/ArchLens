CREATE TABLE IF NOT EXISTS github_pr_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    pr_number INTEGER NOT NULL,
    action VARCHAR(64) NOT NULL,
    head_sha VARCHAR(64) NOT NULL,
    base_sha VARCHAR(64) NOT NULL,
    head_ref VARCHAR(255),
    base_ref VARCHAR(255),
    status VARCHAR(64) NOT NULL DEFAULT 'received',
    comment_id VARCHAR(64),
    summary_markdown TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_github_pr_events_repo_pr ON github_pr_events(repository_id, pr_number);
