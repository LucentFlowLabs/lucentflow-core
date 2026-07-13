-- Per-project daily API request counters for billing and ops visibility.
CREATE TABLE IF NOT EXISTS project_api_usage (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    usage_date DATE NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_api_usage_project_date UNIQUE (project_id, usage_date)
);

CREATE INDEX IF NOT EXISTS idx_project_api_usage_project_date
    ON project_api_usage(project_id, usage_date DESC);
