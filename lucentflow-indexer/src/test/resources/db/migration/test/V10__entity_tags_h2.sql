-- H2 test schema: entity_tags + wider whale tag columns (Module 5)

CREATE TABLE IF NOT EXISTS entity_tags (
    address VARCHAR(42) PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    risk_score_modifier INTEGER NOT NULL DEFAULT 0,
    metadata VARCHAR(4000) NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_entity_tags_category ON entity_tags(category);

ALTER TABLE whale_transactions ALTER COLUMN from_address_tag VARCHAR(100);
ALTER TABLE whale_transactions ALTER COLUMN to_address_tag VARCHAR(100);
ALTER TABLE whale_transactions ALTER COLUMN address_tag VARCHAR(100);

INSERT INTO entity_tags (address, tag_name, category, risk_score_modifier, metadata) VALUES
    (LOWER('0x49ff46ed6b6a0a8ceaecb75429ba6e38c9ac123d'), 'LucentFlow Founder', 'SYSTEM', 0, '{}'),
    (LOWER('0x4200000000000000000000000000000000000010'), 'Base: L2 Standard Bridge', 'BRIDGE', 0, '{}'),
    (LOWER('0x833589fcd6edb6e08f4c7c32d4f71b54bda02913'), 'USDC: Proxy Contract', 'DEFI', 0, '{}'),
    (LOWER('0x94017f291504d6ac5ab78698a44d673752e50529'), 'Aerodrome: Router', 'DEFI', 0, '{}');
