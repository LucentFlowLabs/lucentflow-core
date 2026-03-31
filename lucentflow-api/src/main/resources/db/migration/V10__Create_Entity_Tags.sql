-- LucentTag Oracle: canonical address labels for Metabase / dashboards (Module 5)

CREATE TABLE IF NOT EXISTS entity_tags (
    address VARCHAR(42) PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    risk_score_modifier INTEGER NOT NULL DEFAULT 0,
    metadata TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_entity_tags_category ON entity_tags(category);

-- Long oracle labels (e.g. inferred institution tags)
ALTER TABLE whale_transactions ALTER COLUMN from_address_tag TYPE VARCHAR(100);
ALTER TABLE whale_transactions ALTER COLUMN to_address_tag TYPE VARCHAR(100);
ALTER TABLE whale_transactions ALTER COLUMN address_tag TYPE VARCHAR(100);

INSERT INTO entity_tags (address, tag_name, category, risk_score_modifier, metadata) VALUES
    (LOWER('0x49ff46ed6b6a0a8ceaecb75429ba6e38c9ac123d'), 'LucentFlow Founder', 'SYSTEM', 0, '{}'::jsonb),
    (LOWER('0x4200000000000000000000000000000000000010'), 'Base: L2 Standard Bridge', 'BRIDGE', 0, '{}'::jsonb),
    (LOWER('0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913'), 'USDC: Proxy Contract', 'DEFI', 0, '{}'::jsonb),
    (LOWER('0x94017f291504d6Ac5aB78698A44d673752e50529'), 'Aerodrome: Router', 'DEFI', 0, '{}'::jsonb)
ON CONFLICT (address) DO NOTHING;
