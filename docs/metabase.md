<!--
  LucentFlow Metabase SQL specification (dashboard source of truth).
  @author ArchLucent
  @since 1.0
-->
# LucentFlow BI Intelligence: Metabase SQL Specification
This document serves as the Source of Truth for the LucentFlow monitoring dashboards. It defines the SQL logic used to transform raw Base chain transaction data into actionable security intelligence.

**Schema alignment (v1.1.1-SNAPSHOT):** The `whale_transactions` table exposes analyzer outputs including `risk_score`, `risk_reasons`, `rug_risk_level`, `execution_status` (`SUCCESS` | `REVERTED`), and genesis fields `funding_source_address` / `funding_source_tag`. The `sync_status` table (ID 1 Protocol) exposes heartbeat metrics including `chain_head_block`, `last_scanned_block`, `block_lag`, and `blocks_per_second`.

## 📊 Dashboard Overview
The LucentFlow command center is divided into four critical analytical dimensions:
Whale Sentinel: Real-time capital flow tracking.
Security Sentinel: High-risk contract interaction monitoring.
Macro Ecology: Whale population distribution.
Activity Intelligence: Temporal behavioral analysis.

## [System Health] Sync Pulse | Real-time Block Lag
Objective: Monitor ingestion latency and throughput of the Java 21 Virtual Thread engine.

```sql
SELECT 
    block_lag AS "Sync Lag",
    ROUND(blocks_per_second::numeric, 2) AS "BPS",
    last_scanned_block AS "Current Progress",
    chain_head_block AS "Chain Head",
    updated_at AS "Last Heartbeat"
FROM sync_status
WHERE id = 1;
```

* Visualization: Gauge (Range: 0-10 Green, 10-5000 Blue, 5000+ Grey) and Number cards.

## [Security] Forensic Sentinel | High-Risk Feed
Objective: The primary command feed for high-risk transaction auditing, integrating Risk Scoring, Revert Detection, and Origin Tracing.

```sql
SELECT
    timestamp AS "Audit Time",
    execution_status AS "Status",
    risk_score AS "Score",
    risk_reasons AS "Detection Logic",
    COALESCE(from_address_tag, LEFT(from_address, 6) || '...' || RIGHT(from_address, 4)) AS "Initiator",
    COALESCE(funding_source_tag, 'UNKNOWN') AS "Origin",
    ROUND(value_eth::numeric, 2) || ' ' || COALESCE(token_symbol, 'ETH') AS "Amount",
    hash AS "tx_hash"
FROM whale_transactions
WHERE risk_score >= 20
ORDER BY risk_score DESC, timestamp DESC
LIMIT 50;
```

* Visualization: Table with "Highlight Whole Row" enabled (Score > 80: Deep Red, Score 40-80: Orange).
## 1. Whale Sentinel: Top 10 Inflow (24h)
Objective: Identify the top "magnets" of liquidity on the Base network within the last 24 hours.
```sql
SELECT
    CASE
        WHEN to_address_tag IS NOT NULL THEN '🐳 ' || to_address_tag
        ELSE '👤 ' || SUBSTRING(to_address FROM 1 FOR 6) || '...' || SUBSTRING(to_address FROM 38 FOR 4)
        END AS "Entity",
    to_address AS "Address",
    ROUND(SUM(value_eth)::numeric, 2) AS "Total Inflow (ETH)",
    COUNT(*) AS "Tx Count",
    MAX(timestamp) AS "Last Seen (UTC)"
FROM whale_transactions
WHERE timestamp > NOW() - INTERVAL '24 hours'
AND is_contract_creation = false
AND to_address IS NOT NULL
GROUP BY 1, 2
ORDER BY 3 DESC
LIMIT 10;
```
* Visualization: Table
* Format: Highlight Total Inflow (ETH) > 100 with red background for immediate whale alert.
## 2. Security Sentinel: High-Risk Contract Calls
Objective: Real-time feed of large-value interactions with smart contracts, filtering for potential "Soft-Rugs" or whale exits. Incorporates **risk_score** and on-chain **execution_status** from the Transaction Integrity / risk pipeline.
```sql
SELECT
    timestamp AS "Time (UTC)",
    '🛡️ ' || SUBSTRING(from_address FROM 1 FOR 6) || '...' || SUBSTRING(from_address FROM 38 FOR 4) AS "Initiator",
    '📜 ' || SUBSTRING(to_address FROM 1 FOR 6) || '...' || SUBSTRING(to_address FROM 38 FOR 4) AS "Target Contract",
    ROUND(value_eth::numeric, 2) AS "Value (ETH)",
    COALESCE(risk_score, 0) AS "Risk Score",
    COALESCE(execution_status, 'UNKNOWN') AS "Execution Status",
    CASE
        WHEN execution_status = 'REVERTED' THEN '⚠️ REVERTED (integrity flag)'
        WHEN COALESCE(risk_score, 0) >= 80 THEN '🔴 CRITICAL RISK'
        WHEN COALESCE(risk_score, 0) >= 50 THEN '🟠 ELEVATED RISK'
        WHEN value_eth > 500 THEN '🔴 CRITICAL: WHALE EXIT?'
        WHEN value_eth > 100 THEN '🟠 WARNING: LARGE MOVE'
        ELSE '🟢 REGULAR CALL'
        END AS "Security Alert",
    LEFT(COALESCE(funding_source_tag, ''), 32) AS "Funding Tag",
    hash AS "tx_hash"
FROM whale_transactions
WHERE transaction_type = 'CONTRACT_CALL'
ORDER BY risk_score DESC, timestamp DESC
LIMIT 20;
```
* Visualization: Table
* Action: Map tx_hash to https://basescan.org/tx/{{tx_hash}} for instant on-chain verification.
## 3. Macro Ecology: Whale Class Distribution
Objective: Analyze the "depth of the ocean" by categorizing the population of capital holders.
```sql
SELECT
    CASE
        WHEN value_eth >= 1000 THEN '🐋 Blue Whale (>1000 ETH)'
        WHEN value_eth >= 100 THEN '🦈 Shark (100-1000 ETH)'
        WHEN value_eth >= 10 THEN '🐬 Dolphin (10-100 ETH)'
    ELSE '🦀 Shrimp (<10 ETH)'
    END AS "Whale Class",
    SUM(value_eth) AS "Total Volume (ETH)"
FROM whale_transactions
WHERE timestamp > NOW() - INTERVAL '30 days'
GROUP BY 1
ORDER BY 2 DESC;
```
* Visualization: Donut Chart
* Insight: Used to differentiate between retail-driven hype and institutional-grade accumulation on Base.
## 4. Activity Intelligence: Temporal Heatmap
Objective: Identify the "Golden Hours" of whale activity on Base to optimize monitoring and alert windows.
```sql
SELECT
    EXTRACT(Hour FROM timestamp) AS "Hour_24h",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 1) AS "Mon",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 2) AS "Tue",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 3) AS "Wed",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 4) AS "Thu",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 5) AS "Fri",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 6) AS "Sat",
    COUNT(*) FILTER (WHERE EXTRACT(DOW FROM timestamp) = 0) AS "Sun"
FROM whale_transactions
WHERE timestamp > NOW() - INTERVAL '30 days'
GROUP BY 1
ORDER BY 1;
```
* Visualization: Table (Matrix Mode)
* Format: Use Color Range (Light Blue to Dark Blue) across all weekday columns to visualize peak activity density.