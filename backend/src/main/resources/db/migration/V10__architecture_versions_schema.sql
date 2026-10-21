-- Migration V10: Architecture Versions Schema
CREATE TABLE IF NOT EXISTS architecture_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id UUID NOT NULL REFERENCES architecture_graphs(id) ON DELETE CASCADE,
    version_name VARCHAR(255) NOT NULL,
    description TEXT,
    snapshot_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_architecture_versions_graph ON architecture_versions(graph_id);
