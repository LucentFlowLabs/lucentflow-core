-- Per-project alert rule configuration (P1 Run-1).
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES projects(id),
    min_risk_score INT NOT NULL DEFAULT 70,
    watchlist_only BOOLEAN NOT NULL DEFAULT FALSE,
    contract_creation_only BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_project_id ON alert_rules(project_id);

-- Seed a default rule for every existing project.
INSERT INTO alert_rules (project_id, min_risk_score, watchlist_only, contract_creation_only, enabled)
SELECT p.id, 70, FALSE, FALSE, TRUE
FROM projects p
WHERE NOT EXISTS (
    SELECT 1 FROM alert_rules ar WHERE ar.project_id = p.id
);
