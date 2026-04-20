-- Migrate risk_reasons from VARCHAR to JSONB for structured forensic analytics.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'whale_transactions'
          AND column_name = 'risk_reasons'
    ) THEN
        ALTER TABLE whale_transactions
            ADD COLUMN risk_reasons JSONB NOT NULL DEFAULT '{}'::jsonb;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'whale_transactions'
          AND column_name = 'risk_reasons'
          AND data_type <> 'jsonb'
    ) THEN
        ALTER TABLE whale_transactions
            ALTER COLUMN risk_reasons TYPE JSONB
            USING CASE
                WHEN risk_reasons IS NULL OR btrim(risk_reasons) = '' THEN '{}'::jsonb
                WHEN left(btrim(risk_reasons), 1) = '{' THEN risk_reasons::jsonb
                ELSE jsonb_build_object('LEGACY_REASON', risk_reasons)
            END;
    END IF;
END $$;
