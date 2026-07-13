-- LucentFlow Demo Bootstrap (v1.2.0)
-- LOCAL / DEMO ONLY — do NOT run against production databases.
-- Schema baseline V1 stores only hashed API keys (api_key_hash); no bootstrap plaintext key.
-- Clients still send plaintext X-Project-Key: demo-project-key-2026
-- Usage:
--   psql -h <host> -U <user> -d <db> -f src/main/resources/db/demo_setup.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Demo project with hashed API key and webhook endpoint
INSERT INTO projects (name, api_key_hash, api_key_prefix, webhook_url, is_active)
VALUES (
    'Demo Project',
    encode(digest('demo-project-key-2026', 'sha256'), 'hex'),
    left('demo-project-key-2026', 8),
    'https://webhook.site/replace-with-your-endpoint',
    TRUE
)
ON CONFLICT (api_key_hash) DO UPDATE
SET name = EXCLUDED.name,
    api_key_prefix = EXCLUDED.api_key_prefix,
    webhook_url = EXCLUDED.webhook_url,
    is_active = EXCLUDED.is_active;

-- 2) Default alert rule for demo project (Run-1)
INSERT INTO alert_rules (project_id, min_risk_score, watchlist_only, contract_creation_only, enabled)
SELECT p.id, 70, FALSE, FALSE, TRUE
FROM projects p
WHERE p.api_key_hash = encode(digest('demo-project-key-2026', 'sha256'), 'hex')
ON CONFLICT (project_id) DO NOTHING;

-- 3) Sample API usage for demo dashboards (Run-2)
INSERT INTO project_api_usage (project_id, usage_date, request_count)
SELECT p.id, CURRENT_DATE, 42
FROM projects p
WHERE p.api_key_hash = encode(digest('demo-project-key-2026', 'sha256'), 'hex')
ON CONFLICT (project_id, usage_date)
DO UPDATE SET request_count = EXCLUDED.request_count,
              updated_at = CURRENT_TIMESTAMP;

-- 4) Case #002-style watchlist addresses
WITH demo_project AS (
    SELECT id
    FROM projects
    WHERE api_key_hash = encode(digest('demo-project-key-2026', 'sha256'), 'hex')
    LIMIT 1
)
INSERT INTO watchlist (address, label, category, project_id, created_at)
SELECT * FROM (
    SELECT lower('0x8ba1f109551bD432803012645Ac136ddd64DBA72') AS address,
           'Case002 Factory Root' AS label,
           'BLACKLIST' AS category,
           (SELECT id FROM demo_project) AS project_id,
           CURRENT_TIMESTAMP AS created_at
    UNION ALL
    SELECT lower('0x742d35Cc6634C0532925a3b8D4C9db96C4b4Db45'),
           'Case002 Clone Deployer',
           'MEV_BOT',
           (SELECT id FROM demo_project),
           CURRENT_TIMESTAMP
    UNION ALL
    SELECT lower('0x3f5CE5FBFe3E9af3971dD833D26BA9b5C936f0bE'),
           'Case002 Funding Relay',
           'COMPETITOR',
           (SELECT id FROM demo_project),
           CURRENT_TIMESTAMP
) seeded
ON CONFLICT (project_id, address) DO NOTHING;

-- Quick verification hints:
--   X-Project-Key: demo-project-key-2026
--   X-Admin-Key:   set LUCENTFLOW_ADMIN_API_KEY in .env for /api/v1/admin/projects
-- Endpoints:
--   GET  /api/v1/forensics/events
--   GET  /api/v1/alert-rules
--   GET  /api/v1/usage?days=7
