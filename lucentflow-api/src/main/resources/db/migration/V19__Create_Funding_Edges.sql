-- Genesis Trace 3.0 topology lite: durable funding edges for graph-style forensics.
CREATE TABLE IF NOT EXISTS funding_edges (
    id              BIGSERIAL PRIMARY KEY,
    funder_address  VARCHAR(42) NOT NULL,
    funded_address  VARCHAR(42) NOT NULL,
    hop_layer       INT NOT NULL DEFAULT 1,
    related_tx_hash VARCHAR(66),
    funder_tag      VARCHAR(120),
    blacklisted     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_funding_edges_funded ON funding_edges (funded_address);
CREATE INDEX IF NOT EXISTS idx_funding_edges_funder ON funding_edges (funder_address);
CREATE UNIQUE INDEX IF NOT EXISTS uk_funding_edges_pair_hop
    ON funding_edges (funder_address, funded_address, hop_layer);
