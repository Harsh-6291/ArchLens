-- M3.1: Load Tests & Results Schema
CREATE TABLE load_tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_definition_id UUID NOT NULL REFERENCES api_definitions(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    load_profile VARCHAR(50) NOT NULL DEFAULT 'RAMP_UP', -- CONSTANT, RAMP_UP, STRESS
    virtual_users INT NOT NULL DEFAULT 10,
    duration_seconds INT NOT NULL DEFAULT 30,
    ramp_up_seconds INT NOT NULL DEFAULT 5,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_load_tests_api_definition ON load_tests(api_definition_id);
CREATE INDEX idx_load_tests_status ON load_tests(status);

CREATE TABLE load_test_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_test_id UUID NOT NULL REFERENCES load_tests(id) ON DELETE CASCADE,
    total_requests INT NOT NULL DEFAULT 0,
    successful_requests INT NOT NULL DEFAULT 0,
    failed_requests INT NOT NULL DEFAULT 0,
    requests_per_second DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    p50_latency_ms INT NOT NULL DEFAULT 0,
    p90_latency_ms INT NOT NULL DEFAULT 0,
    p95_latency_ms INT NOT NULL DEFAULT 0,
    p99_latency_ms INT NOT NULL DEFAULT 0,
    min_latency_ms INT NOT NULL DEFAULT 0,
    max_latency_ms INT NOT NULL DEFAULT 0,
    avg_latency_ms INT NOT NULL DEFAULT 0,
    error_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status_codes JSONB NOT NULL DEFAULT '{}',
    capacity_threshold VARCHAR(50) NOT NULL DEFAULT 'SAFE', -- SAFE, WARNING, SATURATED, FAILED
    bottleneck_diagnosis TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (load_test_id)
);

CREATE INDEX idx_load_test_results_load_test ON load_test_results(load_test_id);
