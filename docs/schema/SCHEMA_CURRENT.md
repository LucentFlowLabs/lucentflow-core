# LucentFlow Database Schema (Current)

**Status:** Flyway **V1** baseline plus **V2** plan/quota columns plus **V3** watchlist occupancy.  
**Runtime authority:** [`lucentflow-api/src/main/resources/db/migration/`](../../lucentflow-api/src/main/resources/db/migration/)  
**Docs mirror:** [`schema_current.sql`](schema_current.sql)

Further schema changes: add `V4__...sql` (and refresh this page).

---

## Domain layers

| Layer | Tables | Scope |
|-------|--------|--------|
| Global chain facts | `whale_transactions`, `funding_edges`, `entity_tags` | Shared across all projects |
| Indexer checkpoint | `sync_status` | Singleton row `id = 1` |
| Tenant configuration | `projects`, `watchlist`, `alert_rules`, `project_api_usage`, `project_watchlist_usage` | B2B isolation |
| Cluster coordination | `worker_leases`, `api_rate_limit_buckets` | Multi-node lease / rate fairness |

Tenant **read** isolation for forensics is application-layer: intersect global facts with the project’s `watchlist`. Topology **lookup address** is watchlist-gated; after a hit, returned `funding_edges` are the global graph. Entity tags remain shared intel. Risk Score and the public ETH/USD oracle are not watchlist-gated.

---

## Entity relationship

```mermaid
erDiagram
    projects ||--o{ watchlist : "project_id"
    projects ||--o| alert_rules : "project_id UNIQUE"
    projects ||--o{ project_api_usage : "project_id"
    projects ||--o| project_watchlist_usage : "project_id"

    whale_transactions ||..o{ funding_edges : "related_tx_hash soft"
    entity_tags ||..o{ whale_transactions : "tags denormalized"

    projects {
        bigint id PK
        varchar api_key_hash UK
        varchar webhook_secret
    }
    watchlist {
        bigint id PK
        bigint project_id FK
        varchar address
    }
    alert_rules {
        bigint id PK
        bigint project_id FK_UK
    }
    project_api_usage {
        bigint id PK
        bigint project_id FK
        date usage_date
    }
    project_watchlist_usage {
        bigint project_id PK_FK
        int address_count
    }
    whale_transactions {
        bigint id PK
        varchar hash UK
    }
    funding_edges {
        bigint id PK
        varchar funder_address
        varchar funded_address
    }
    entity_tags {
        varchar address PK
    }
    sync_status {
        bigint id PK
    }
    worker_leases {
        varchar lease_name PK
    }
    api_rate_limit_buckets {
        varchar bucket_key PK
        bigint epoch_minute PK
    }
```

---

## Design conventions

### ID=1 Protocol (`sync_status`)

- Exactly one checkpoint row with `id = 1` (`CHECK (id = 1)`).
- V1 seeds `(id=1, last_scanned_block=0, sync_status='ACTIVE')`.
- `worker_leases` / `WorkerLeaseCoordinator` provide TTL leader election that gates indexer/analyzer when `lucentflow.lease.enabled=true`.
- **Deploy policy:** still run a single worker replica until multi-replica failover is validated (lease is defense-in-depth).

### Native UPSERT

Prefer PostgreSQL `ON CONFLICT` for high-throughput writers:

| Table | Conflict target | Typical action |
|-------|-----------------|----------------|
| `whale_transactions` | `(hash)` | `DO UPDATE` enrichment columns |
| `funding_edges` | `(funder, funded, hop_layer)` | `DO NOTHING` |
| `project_api_usage` | `(project_id, usage_date)` | increment `request_count` if under quota; refund on non-2xx |
| `api_rate_limit_buckets` | `(bucket_key, epoch_minute)` | increment `request_count` |
| `worker_leases` | `(lease_name)` | conditional reclaim / renew |

### Multi-tenant boundary

- **Scoped:** `watchlist`, `alert_rules` (1:1), `project_api_usage`, API auth via `projects.api_key_hash`.
- **Shared:** whale facts, funding graph, entity tags, sync/lease/rate tables.
- Forensics: empty project watchlist ⇒ empty result set (`X-LucentFlow-Scope: watchlist-empty`).
- Topology API is watchlist-gated: addresses not on the project list return empty edges (`scope=watchlist-miss`). Point-lookup risk scores use `POST /api/v1/risk/score` and do not read the global graph through topology.
- No bootstrap plaintext API key in V1; create tenants via Admin API or [`demo_setup.sql`](../../lucentflow-api/src/main/resources/db/demo_setup.sql) (local only).

### Dual-write labels

- Canonical source: `entity_tags` (V1 seeds a few Base/system labels).
- Denormalized on `whale_transactions.*_tag` / `funding_source_tag` for query and dashboards.

### Schema ownership in code

| Concern | Location |
|---------|----------|
| Flyway migrations | `lucentflow-api` (`classpath:db/migration`) |
| JPA entities / repositories | `lucentflow-common` (`.../entity`, `.../repository`) |
| Lease / shared rate limit | JDBC services in `lucentflow-common` (no JPA entity) |
| Indexer / Analyzer DDL | `ddl-auto: none` — never evolve schema |

---

## Table inventory

### `whale_transactions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL` | PK |
| `hash` | `VARCHAR(66)` | UNIQUE NOT NULL |
| `from_address` / `to_address` | `VARCHAR(42)` | `to_address` NULL on create |
| `value_eth` | `DECIMAL(38,18)` | NOT NULL |
| `block_number` | `BIGINT` | NOT NULL |
| `timestamp` | `TIMESTAMPTZ` | NOT NULL |
| `is_contract_creation` | `BOOLEAN` | DEFAULT FALSE |
| `gas_price` / `gas_limit` | `NUMERIC(78,0)` | wei-scale |
| `gas_cost_eth` | `DECIMAL(38,18)` | |
| `transaction_type` | `VARCHAR(20)` | DEFAULT `'UNKNOWN'` |
| `*_tag` / categories | `VARCHAR` | denormalized labels |
| `funding_source_*` / `rug_risk_level` | | anti-rug |
| `risk_score` | `INT` | |
| `risk_reasons` | `JSONB` | NOT NULL DEFAULT `'{}'` |
| `execution_status` | `VARCHAR(20)` | DEFAULT `'SUCCESS'` |
| `bytecode_hash` | `VARCHAR(64)` | creation fingerprint |
| `token_symbol` / `token_address` | | ERC-20 outpost |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | trigger on update |

**Indexes:** from/to (partial), block, timestamp, value, hash, contract creation (partial), funding source, rug level, serial deployer partial composite, bytecode_hash, token_address.

### `sync_status`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL` | PK + `CHECK (id = 1)` |
| `last_scanned_block` | `BIGINT` | NOT NULL |
| `sync_status` | `VARCHAR(20)` | DEFAULT `'ACTIVE'` |
| `chain_head_block` / `block_lag` / `blocks_per_second` | | metrics |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | |

### `entity_tags`

| Column | Type | Notes |
|--------|------|-------|
| `address` | `VARCHAR(42)` | PK (lowercase in app) |
| `tag_name` | `VARCHAR(100)` | NOT NULL |
| `category` | `VARCHAR(32)` | NOT NULL |
| `risk_score_modifier` | `INTEGER` | DEFAULT 0 |
| `metadata` | `TEXT` | DEFAULT `'{}'` (not JSONB) |

### `funding_edges`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL` | PK |
| `funder_address` / `funded_address` | `VARCHAR(42)` | NOT NULL |
| `hop_layer` | `INT` | DEFAULT 1 |
| `related_tx_hash` | `VARCHAR(66)` | soft link, no FK |
| `funder_tag` | `VARCHAR(120)` | |
| `blacklisted` | `BOOLEAN` | DEFAULT FALSE |
| `created_at` | `TIMESTAMPTZ` | |

**Unique:** `(funder_address, funded_address, hop_layer)`.

### `projects`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL` | PK |
| `name` | `VARCHAR(120)` | NOT NULL (no DB UNIQUE) |
| `api_key_hash` | `VARCHAR(64)` | UNIQUE NOT NULL |
| `api_key_prefix` | `VARCHAR(16)` | display only |
| `webhook_url` | `VARCHAR(1024)` | |
| `webhook_secret` | `VARCHAR(256)` | plaintext; NULL → global fallback |
| `is_active` | `BOOLEAN` | DEFAULT TRUE |
| `plan` | `VARCHAR(32)` | `BUILDER` / `DESK` / `PROTOCOL` (CHECK) |
| `daily_request_quota` | `INT` | DEFAULT 2000; `0` disables the daily cap |
| `watchlist_limit` | `INT` | DEFAULT 50; `0` disables the address cap |
| `created_at` | `TIMESTAMPTZ` | |

### `watchlist`

**Unique:** `(project_id, address)`. FK → `projects(id)`.

### `alert_rules`

One row per project (`project_id` UNIQUE + FK).

### `project_api_usage`

Daily counters: UNIQUE `(project_id, usage_date)`.

### `project_watchlist_usage`

Per-project occupancy (`project_id` PK + FK). `WatchlistCapLedger` reserves with `ON CONFLICT … WHERE address_count < watchlist_limit` (same pattern as daily quota). `0` on `projects.watchlist_limit` disables the cap.

### `worker_leases` / `api_rate_limit_buckets`

Cluster lease and per-minute rate buckets (no retention job in schema).

---

## Known design debt (not changed in V1 squash)

1. Topology lookup is watchlist-gated; edges after a hit and entity tags remain global shared intel. Public oracle and `POST /risk/score` are not watchlist-gated (by design).
2. `webhook_secret` stored in plaintext vs hashed API keys.
3. `api_rate_limit_buckets` grows without TTL / cleanup.
4. FKs use default `NO ACTION` (no cascade / soft-delete lifecycle for projects).
5. `WatchlistRepository.findByAddress` is project-unscoped (prefer `*AndProjectId` APIs).
6. Missing dedicated `risk_score` index; some redundant indexes.
7. `entity_tags.metadata` is `TEXT` rather than JSONB.

---

## How to use this doc

- **Onboarding / review:** this page + ER diagram.
- **Apply schema:** Flyway runs `V1__init_schema.sql` then `V2` / `V3` on empty databases.
- **Local demo tenant:** [`demo_setup.sql`](../../lucentflow-api/src/main/resources/db/demo_setup.sql) after migrate.
- **Evolve:** add `V4__...`, then update this snapshot.
