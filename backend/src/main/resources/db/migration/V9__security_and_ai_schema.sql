-- Migration V9: Security Findings & AI Recommendations
CREATE TABLE IF NOT EXISTS security_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id UUID NOT NULL REFERENCES architecture_graphs(id) ON DELETE CASCADE,
    graph_node_id UUID REFERENCES graph_nodes(id) ON DELETE SET NULL,
    evidence_id VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW, INFO
    title VARCHAR(255) NOT NULL,
    category VARCHAR(128) NOT NULL, -- AUTH, INJECTION, DATA_EXPOSURE, MISCONFIGURATION, DEPENDENCY
    description TEXT NOT NULL,
    remediation TEXT NOT NULL,
    source_file VARCHAR(512),
    line_number INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_findings_graph ON security_findings(graph_id);
CREATE INDEX IF NOT EXISTS idx_security_findings_node ON security_findings(graph_node_id);
CREATE INDEX IF NOT EXISTS idx_security_findings_evidence_id ON security_findings(evidence_id);

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id UUID NOT NULL REFERENCES architecture_graphs(id) ON DELETE CASCADE,
    problem TEXT NOT NULL,
    why TEXT NOT NULL,
    evidence_ids JSONB NOT NULL DEFAULT '[]',
    recommendation TEXT NOT NULL,
    impact TEXT NOT NULL,
    considerations TEXT NOT NULL,
    hallucination_verified BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_recommendations_graph ON ai_recommendations(graph_id);
