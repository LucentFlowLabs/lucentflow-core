-- Shared per-minute API rate buckets and TTL worker leader leases.
-- Prerequisites for multi-node API rate fairness and (later) multi-replica workers.
--
-- @author ArchLucent
-- @since 1.2
CREATE TABLE IF NOT EXISTS worker_leases (
    lease_name  VARCHAR(64) PRIMARY KEY,
    holder_id   VARCHAR(128) NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_rate_limit_buckets (
    bucket_key    VARCHAR(256) NOT NULL,
    epoch_minute  BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_key, epoch_minute)
);

COMMENT ON TABLE worker_leases IS 'TTL leader election for indexer/analyzer (sync_status ID=1 writer)';
COMMENT ON TABLE api_rate_limit_buckets IS 'Cluster-shared per-minute API rate limit counters';
