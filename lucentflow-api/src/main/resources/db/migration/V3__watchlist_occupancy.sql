-- Atomic watchlist cap: occupancy counter per project (same UPSERT pattern as daily quota).
-- Concurrent creates cannot overshoot watchlist_limit; 0 still means unlimited.

CREATE TABLE project_watchlist_usage (
    project_id     BIGINT PRIMARY KEY REFERENCES projects (id),
    address_count  INT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_project_watchlist_usage_count CHECK (address_count >= 0)
);

COMMENT ON TABLE project_watchlist_usage IS
    'Per-project watchlist occupancy; reserved with ON CONFLICT … WHERE address_count < watchlist_limit';
COMMENT ON COLUMN project_watchlist_usage.address_count IS
    'Live address rows for the project; decremented on delete or failed insert refund';

INSERT INTO project_watchlist_usage (project_id, address_count, updated_at)
SELECT p.id,
       COALESCE(w.cnt, 0),
       CURRENT_TIMESTAMP
FROM projects p
LEFT JOIN (
    SELECT project_id, COUNT(*)::INT AS cnt
    FROM watchlist
    GROUP BY project_id
) w ON w.project_id = p.id;
