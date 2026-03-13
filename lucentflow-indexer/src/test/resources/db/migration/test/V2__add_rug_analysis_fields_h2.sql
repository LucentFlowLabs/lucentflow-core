-- Add anti-rug analysis fields to whale_transactions table (H2 Compatible)
-- V2__add_rug_analysis_fields.sql

ALTER TABLE whale_transactions 
ADD COLUMN funding_source_address VARCHAR(42),
ADD COLUMN funding_source_tag VARCHAR(100),
ADD COLUMN rug_risk_level VARCHAR(20);

-- Add indexes for new anti-rug fields (H2 compatible)
CREATE INDEX IF NOT EXISTS idx_wt_funding_source_address ON whale_transactions(funding_source_address);
CREATE INDEX IF NOT EXISTS idx_wt_rug_risk_level ON whale_transactions(rug_risk_level);
