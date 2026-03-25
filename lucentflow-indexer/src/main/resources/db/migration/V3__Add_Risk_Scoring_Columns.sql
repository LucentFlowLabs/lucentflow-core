-- Add risk scoring columns to whale_transactions table
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'risk_score'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN risk_score INT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'risk_reasons'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN risk_reasons VARCHAR(1000);
    END IF;
END $$;
