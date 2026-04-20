-- Project scope for multi-project isolation.
CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    api_key VARCHAR(128) NOT NULL UNIQUE,
    webhook_url VARCHAR(1024),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bootstrap a default project for backward compatibility.
INSERT INTO projects (name, api_key, webhook_url, is_active)
VALUES ('Default Project', 'default-dev-key', NULL, TRUE)
ON CONFLICT (api_key) DO NOTHING;

-- Add project scope to existing watchlist table.
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS project_id BIGINT;

UPDATE watchlist
SET project_id = (SELECT id FROM projects WHERE api_key = 'default-dev-key' LIMIT 1)
WHERE project_id IS NULL;

ALTER TABLE watchlist
    ALTER COLUMN project_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'watchlist'
          AND constraint_name = 'fk_watchlist_project'
    ) THEN
        ALTER TABLE watchlist
            ADD CONSTRAINT fk_watchlist_project
            FOREIGN KEY (project_id) REFERENCES projects(id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'watchlist_address_key'
    ) THEN
        ALTER TABLE watchlist DROP CONSTRAINT watchlist_address_key;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_watchlist_project_address
    ON watchlist(project_id, address);

CREATE INDEX IF NOT EXISTS idx_watchlist_project_id ON watchlist(project_id);
