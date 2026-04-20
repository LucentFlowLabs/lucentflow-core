-- LucentFlow Demo Bootstrap (Day 7)
-- Usage:
--   psql -h <host> -U <user> -d <db> -f src/main/resources/db/demo_setup.sql

-- 1) Demo project with API key and webhook endpoint
INSERT INTO projects (name, api_key, webhook_url, is_active)
VALUES (
    'Demo Project',
    'demo-project-key-2026',
    'https://webhook.site/replace-with-your-endpoint',
    TRUE
)
ON CONFLICT (api_key) DO UPDATE
SET name = EXCLUDED.name,
    webhook_url = EXCLUDED.webhook_url,
    is_active = EXCLUDED.is_active;

-- 2) Add Case #002-style watchlist addresses
WITH demo_project AS (
    SELECT id
    FROM projects
    WHERE api_key = 'demo-project-key-2026'
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

-- 3) Output quick verification hints
-- API key to use in Swagger or curl:
--   X-Project-Key: demo-project-key-2026
