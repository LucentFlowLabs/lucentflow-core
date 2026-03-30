-- Genesis Trace 2.0 — Recursive origin auditing (PostgreSQL 16)
--
-- Purpose:
--   From a target EOA/contract address, find the earliest non-zero ETH inflow in
--   whale_transactions, then walk up to three hops (layers) toward the ultimate
--   funder. Depth is capped in SQL (t.layer < 3 on the recursive branch) so the
--   query cannot recurse indefinitely or blow the Java stack (no JVM recursion).
--
-- Notes:
--   - Uses whale_transactions only (indexed corpus). Addresses with no matching rows
--     yield an empty result; the application may fall back to explorer APIs.
--   - CEX / Bridge / Mixer / blacklist labels are applied in Java (CreatorFundingTracer)
--     so allowlists can evolve without schema churn.
--
-- @author ArchLucent
-- @since 1.0

-- JdbcTemplate uses a single bound parameter (address to trace).
-- For psql testing, replace ? with a literal or use PREPARE.

WITH RECURSIVE target AS (
    SELECT LOWER(TRIM('0x0000000000000000000000000000000000000000'::text)) AS addr
),
trace AS (
    -- Layer 1: earliest non-zero inflow TO the target address
    SELECT
        1 AS layer,
        tgt.addr AS recipient_inspected,
        LOWER(TRIM(w.from_address))::varchar(42) AS funder_address,
        w.hash::varchar(66) AS inflow_hash,
        w.block_number AS inflow_block,
        w.value_eth AS inflow_value_eth
    FROM target tgt
    INNER JOIN LATERAL (
        SELECT wt.*
        FROM whale_transactions wt
        WHERE LOWER(TRIM(wt.to_address)) = tgt.addr
          AND wt.value_eth > 0::numeric
        ORDER BY wt.block_number ASC, wt.id ASC
        LIMIT 1
    ) w ON TRUE

    UNION ALL

    -- Layers 2–3: earliest non-zero inflow TO the previous hop's funder
    SELECT
        t.layer + 1,
        t.funder_address AS recipient_inspected,
        LOWER(TRIM(w2.from_address))::varchar(42),
        w2.hash::varchar(66),
        w2.block_number,
        w2.value_eth
    FROM trace t
    INNER JOIN LATERAL (
        SELECT wt.*
        FROM whale_transactions wt
        WHERE LOWER(TRIM(wt.to_address)) = t.funder_address
          AND wt.value_eth > 0::numeric
        ORDER BY wt.block_number ASC, wt.id ASC
        LIMIT 1
    ) w2 ON TRUE
    WHERE t.layer < 3
      AND t.funder_address IS NOT NULL
)
SELECT
    layer,
    recipient_inspected,
    funder_address,
    inflow_hash,
    inflow_block,
    inflow_value_eth
FROM trace
ORDER BY layer DESC
LIMIT 1;
