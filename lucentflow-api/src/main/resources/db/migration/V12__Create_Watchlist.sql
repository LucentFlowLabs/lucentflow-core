-- Create watchlist table for targeted intel monitoring.
CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(42) NOT NULL UNIQUE,
    label VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_watchlist_address ON watchlist(address);
CREATE INDEX IF NOT EXISTS idx_watchlist_category ON watchlist(category);
