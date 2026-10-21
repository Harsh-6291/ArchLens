-- Migration V5: Environments
-- Stores named variable sets (e.g. local, staging, production) used for API execution.
-- Secret-flagged variables are encrypted individually at rest.

CREATE TABLE IF NOT EXISTS environments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    variables JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_env_name UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_environments_user_id ON environments(user_id);
