-- Migration V4: API Definitions
-- Stores discovered API routes extracted by the Tier 2 Spring Boot detector.
-- Each row represents one HTTP endpoint linked to its owning service graph node.

CREATE TABLE IF NOT EXISTS api_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_id UUID NOT NULL REFERENCES architecture_graphs(id) ON DELETE CASCADE,
    graph_node_id UUID NOT NULL REFERENCES graph_nodes(id) ON DELETE CASCADE,
    http_method VARCHAR(16) NOT NULL,        -- GET, POST, PUT, DELETE, PATCH, etc.
    path VARCHAR(512) NOT NULL,              -- full resolved path, e.g. /api/v1/users/{id}
    handler_class VARCHAR(255),             -- simple class name of the @RestController
    handler_method VARCHAR(255),            -- method name in that class
    source_file VARCHAR(512),               -- relative path to source .java file
    line_number INT,                        -- 1-based line number of the handler method
    metadata JSONB,                         -- extra info (params, consumes, produces, etc.)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_defs_graph_id ON api_definitions(graph_id);
CREATE INDEX IF NOT EXISTS idx_api_defs_node_id  ON api_definitions(graph_node_id);
