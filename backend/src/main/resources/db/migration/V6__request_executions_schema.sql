-- Migration V6: Request Executions
-- Stores history and telemetry of API requests executed through the server-side proxy.

CREATE TABLE IF NOT EXISTS request_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_definition_id UUID REFERENCES api_definitions(id) ON DELETE CASCADE,
    saved_request_id UUID,
    status_code INT,
    latency_ms INT,
    response_headers JSONB,
    response_body_excerpt TEXT,
    error TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_req_exec_api_def_id ON request_executions(api_definition_id);
CREATE INDEX IF NOT EXISTS idx_req_exec_executed_at ON request_executions(executed_at);
