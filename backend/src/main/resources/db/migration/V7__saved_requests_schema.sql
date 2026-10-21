-- M2.5: Saved Requests — named HTTP request templates for an API definition
CREATE TABLE saved_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_definition_id UUID NOT NULL REFERENCES api_definitions(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    -- Request data (mirrors ExecuteApiRequest fields)
    path_params  JSONB NOT NULL DEFAULT '{}',
    query_params JSONB NOT NULL DEFAULT '{}',
    headers      JSONB NOT NULL DEFAULT '{}',
    body         JSONB,
    environment_id UUID REFERENCES environments(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (api_definition_id, name)
);

CREATE INDEX idx_saved_requests_api_definition_id ON saved_requests(api_definition_id);
