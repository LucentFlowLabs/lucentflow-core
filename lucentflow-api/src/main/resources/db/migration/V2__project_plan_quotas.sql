-- Per-project commercial plan: daily API quota and watchlist cap.
-- Global LUCENTFLOW_API_DAILY_REQUEST_QUOTA remains a fallback when a row is missing.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS plan VARCHAR(32) NOT NULL DEFAULT 'BUILDER',
    ADD COLUMN IF NOT EXISTS daily_request_quota INT NOT NULL DEFAULT 2000,
    ADD COLUMN IF NOT EXISTS watchlist_limit INT NOT NULL DEFAULT 50;

ALTER TABLE projects DROP CONSTRAINT IF EXISTS chk_projects_plan;
ALTER TABLE projects
    ADD CONSTRAINT chk_projects_plan CHECK (plan IN ('BUILDER', 'DESK', 'PROTOCOL'));

COMMENT ON COLUMN projects.plan IS 'Commercial tier: BUILDER (default), DESK, PROTOCOL';
COMMENT ON COLUMN projects.daily_request_quota IS 'Daily 2xx-counted API requests; 0 disables the daily cap for this project';
COMMENT ON COLUMN projects.watchlist_limit IS 'Max watchlist rows; 0 disables the cap for this project';
