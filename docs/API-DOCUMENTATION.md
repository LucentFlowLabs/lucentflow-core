# LucentFlow API Documentation

## Overview

The LucentFlow API provides real-time whale transaction monitoring and blockchain synchronization endpoints for Base network. Built with Spring Boot 3.4 and secured with comprehensive error handling, API delivers precise blockchain data with 18-decimal precision for cryptocurrency values.

---

## Base URL

```
http://localhost:8080/api/v1
```

## Swagger UI

Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui/index.html
```

---

## Core Endpoints

### 1. System Health

#### GET /actuator/health

**Description:** Primary system health verification endpoint for uptime monitoring and load balancer health checks. As of **v1.1.0-STABLE**, the aggregate status incorporates **JSON-RPC reachability** via `JsonRpcHealthIndicator`: operators can tell **database-up** from **RPC-up** without a separate probe.

**Contributors (representative):**

| Component | Meaning |
|-----------|---------|
| `db` | PostgreSQL pool / connectivity |
| `jsonRpc` | `eth_blockNumber` against the configured Web3j endpoint (`rpc`: `reachable` \| `error` \| `unreachable`; optional `blockNumber` when UP) |
| `diskSpace` | Host disk threshold (when enabled) |

**Response (illustrative):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "jsonRpc": {
      "status": "UP",
      "details": {
        "rpc": "reachable",
        "blockNumber": "0x1234abcd"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 250685575168,
        "free": 125342771712,
        "threshold": 10485760
      }
    }
  }
}
```

If the RPC endpoint is down or times out, `jsonRpc` reports **DOWN** with `rpc` / `error` / `message` details, and the **aggregate JSON** `status` is typically **DOWN**. HTTP status on **`GET /actuator/health`** is still **200** (`management.endpoint.health.status.http-mapping`) so operator dashboards receive a body they can parse.

**Kubernetes / Compose probes (decided policy):** RPC DOWN must **not** restart the worker (the in-memory `TransactionPipe` is at-most-once) or take API pods out of rotation.

| Probe | Path | Includes | HTTP when down |
|-------|------|----------|----------------|
| Liveness | `/actuator/health/liveness` | `livenessState` only | 503 |
| Readiness | `/actuator/health/readiness` | `readinessState` + `db` | 503 |
| Operator aggregate | `/actuator/health` | `db`, `jsonRpc`, … | 200 even if `jsonRpc` is DOWN |

Component details on the aggregate endpoint are gated by `show-details: when_authorized`.

**Usage:**
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
```

---

### 2. Blockchain Synchronization Status

#### GET /api/v1/sync-status

**Description:** Retrieve current blockchain synchronization status including last scanned block and pipeline state.

`lastScannedBlock` is the ID=1 **enqueue** high-water: whale candidates from that height have been pushed onto `TransactionPipe`. Analyzer UPSERT is asynchronous. A process crash after this height advances and before persist is **at-most-once** for those transaction hashes (they are not re-scanned). A lost checkpoint reprocesses via idempotent UPSERT.

**Response Fields (ID=1 Protocol):**
- `lastScannedBlock`: Highest block whose whale candidates were enqueued (`0` when not started). Not “UPSERT completed”.
- `chainHeadBlock`: Chain tip at last indexer heartbeat (nullable)
- `blockLag`: `chainHeadBlock - lastScannedBlock` (nullable)
- `blocksPerSecond`: Approximate indexing throughput (nullable)
- `syncStatus`: `ACTIVE` when row **id=1** exists in `sync_status`, otherwise `NOT_STARTED`
- `createdAt` / `updatedAt`: Row timestamps as ISO-8601 UTC (nullable when not started)

**Example Request:**
```bash
curl "http://localhost:8080/api/v1/sync-status"
```

**Response Format (indexed):**
```json
{
  "lastScannedBlock": 43213473,
  "chainHeadBlock": 43213510,
  "blockLag": 37,
  "blocksPerSecond": 12.5,
  "createdAt": "2024-03-17T03:06:58.769Z",
  "updatedAt": "2024-03-17T03:07:00.483Z",
  "syncStatus": "ACTIVE"
}
```

**Response Format (no sync row yet):**
```json
{
  "lastScannedBlock": 0,
  "chainHeadBlock": null,
  "blockLag": null,
  "blocksPerSecond": null,
  "createdAt": null,
  "updatedAt": null,
  "syncStatus": "NOT_STARTED"
}
```

**Note:** As of **v1.2.0-STABLE**, `chainHeadBlock`, `blockLag`, and `blocksPerSecond` are included in this JSON response. Metabase SQL dashboards remain available in `docs/metabase.md`.

---

### 3. Whale Transaction Query

#### GET /api/v1/whales

**Description:** Retrieve paginated list of whale transactions with optional minimum ETH value filter. All monetary values are returned as 18-decimal precision strings to prevent floating-point precision errors.

**Parameters:**
- `minEth` (optional, query): Minimum ETH value to filter transactions (e.g., 10.5)
- `page` (optional, query): Page number (0-based, default: 0)
- `size` (optional, query): Page size (max 100, default: 20)

**Example Requests:**
```bash
# Get all whale transactions (first page)
curl "http://localhost:8080/api/v1/whales"

# Get whale transactions with minimum 50 ETH
curl "http://localhost:8080/api/v1/whales?minEth=50.0"

# Get page 2 with 10 items per page
curl "http://localhost:8080/api/v1/whales?page=1&size=10"
```

**Response Format:** Spring `Page<WhaleTransaction>` JSON. Monetary fields use numeric JSON for `BigDecimal` / `BigInteger` per Jackson defaults; clients should parse **`valueEth`** as a fixed-scale decimal string in application logic.

```json
{
  "content": [
    {
      "id": 1,
      "hash": "0x2eac0688d67bb7a488b5b1dc734cedf5c4b04c588f69fbc12231061c12254921",
      "fromAddress": "0x742d35Cc6634C0532925a3b844Bc9e2292D828",
      "toAddress": "0x1234567890123456789012345678901234567890",
      "valueEth": 150.500000000000000000,
      "blockNumber": 43213474,
      "timestamp": "2024-03-17T03:04:13.501Z",
      "isContractCreation": false,
      "gasPrice": "20000000000",
      "gasLimit": "21000",
      "gasCostEth": 0.000000000000000001,
      "transactionType": "REGULAR_TRANSFER",
      "fromAddressTag": "UNKNOWN",
      "toAddressTag": "Coinbase Proxy",
      "whaleCategory": "MEGA_WHALE",
      "addressTag": "UNKNOWN",
      "transactionCategory": "EXCHANGE",
      "fundingSourceAddress": null,
      "fundingSourceTag": null,
      "rugRiskLevel": "LOW",
      "riskScore": 10,
      "riskReasons": null,
      "executionStatus": null,
      "bytecodeHash": null,
      "tokenSymbol": null,
      "tokenAddress": null,
      "createdAt": "2024-03-17T03:04:13.501Z",
      "updatedAt": "2024-03-17T03:04:13.501Z"
    }
  ],
  "pageable": {
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "pageNumber": 0,
    "pageSize": 20,
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "first": true,
  "numberOfElements": 20,
  "size": 20,
  "empty": false
}
```

---

### 4. Whale Transaction Statistics

#### GET /api/v1/whales/stats

**Description:** Retrieve comprehensive statistics about whale transactions including total count, largest transaction, and database status.

**Example Request:**
```bash
curl "http://localhost:8080/api/v1/whales/stats"
```

**Response Format:** Flat map produced by `WhaleQueryController#getWhaleStatistics`. The **`largestWhaleTransaction`** object is **omitted** when the table is empty.

```json
{
  "totalWhaleTransactions": 150,
  "databaseStatus": "CONNECTED",
  "lastUpdated": 1704110400000,
  "largestWhaleTransaction": {
    "hash": "0xabc123def4567890123456789012345678901234567890",
    "valueEth": 2500.000000000000000000,
    "fromAddress": "0xdef4567890123456789012345678901234567890",
    "toAddress": "0x1234567890123456789012345678901234567890",
    "timestamp": "2024-03-17T03:00:00.000Z"
  }
}
```

---

### 5. Tag Oracle (institutional labels)

There is **no** dedicated REST controller for the Tag Oracle in **v1.1.0**. Labels are **sovereign, local-first** data:

- **Storage:** `entity_tags` (Flyway **V1** baseline + operator imports) holds canonical **address → label** rows used by **`TagOracleService`** in the analyzer.
- **API surfacing:** Resolved tags appear on whale payloads as **`fromAddressTag`**, **`toAddressTag`**, **`addressTag`**, and related fields on **`GET /api/v1/whales`** when the pipeline has enriched the row.
- **Scale:** V1 seeds a small curated set (a handful of Base/system labels). Full-scale deployments may import **hundreds** of institutional labels; **row count is deployment-dependent** — do not assume a fixed catalog size from the API alone.
- **Ad-hoc inspection:** Operators may query PostgreSQL directly, e.g. `SELECT address, tag_name, category FROM entity_tags ORDER BY tag_name LIMIT 50;`, or attach Metabase for governance workflows.

---

## Data Precision Standards

### Cryptocurrency Value Handling

**Critical Requirement:** Treat monetary fields as **decimal-safe** end to end. Jackson typically serializes JPA **`BigDecimal`** fields (e.g. **`valueEth`**, **`gasCostEth`**) as JSON **numbers**; never use raw `double` arithmetic in clients.

**valueEth Field Format:**
- **Semantics**: 18-decimal-scale ETH amount (schema `precision=38, scale=18`)
- **Example**: `150.500000000000000000` (JSON number) or an equivalent string form if you configure Jackson otherwise
- **Client Handling**: Use `BigDecimal` (Java) or `BigNumber.js` (JavaScript) — **parse from string** if your client receives quoted decimals

**Recommended Client Implementation:**

**Java (Backend):**
```java
import java.math.BigDecimal;

// Parse ETH value safely
BigDecimal ethValue = new BigDecimal(transaction.getValueEth());
ethValue = ethValue.setScale(18, RoundingMode.HALF_UP);

// Convert to wei
BigDecimal weiValue = ethValue.multiply(new BigDecimal("10").pow(18));
```

**JavaScript (Frontend):**
```javascript
import BigNumber from 'bignumber.js';

// Parse ETH value safely
const ethValue = new BigNumber(transaction.valueEth);
const weiValue = ethValue.times(new BigNumber('10').pow(18));

// Format for display
const displayValue = ethValue.div(new BigNumber('10').pow(18)).toFixed(4);
```

**Timestamp Standard:** All timestamp fields follow ISO-8601 UTC format (e.g., `2024-03-17T03:04:13.501Z`). Ensure proper timezone handling in client applications.

---

## Whale Classification System

### Value Categories

| Category | ETH Range | Description |
|-----------|------------|-------------|
| **WHALE** | 10-100 ETH | Standard whale transactions |
| **MEGA_WHALE** | 100-1000 ETH | Large institutional movements |
| **GIGA_WHALE** | 1000+ ETH | Protocol-level transactions |
| **FRESH_WHALE** | Any value from new addresses | First-time large transfers |

### Transaction Types

| Type | Description |
|-------|-------------|
| **REGULAR_TRANSFER** | Standard ERC-20 transfers |
| **EXCHANGE** | Exchange interactions (Coinbase, etc.) |
| **DEFI** | DeFi protocol operations |
| **CONTRACT_CREATION** | New smart contract deployments |
| **FRESH_WHALE** | High-value transfers from unknown addresses |

---

## Error Handling

### Standard Error Responses

#### 400 Bad Request
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid parameters provided",
  "path": "/api/v1/whales",
  "validationErrors": [
    {
      "field": "minEth",
      "message": "Minimum ETH value must be positive"
    }
  ]
}
```

#### 401 Unauthorized
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

#### 404 Not Found
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found"
}
```

#### 429 Rate Limited
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later.",
  "retryAfter": 60
}
```

#### 500 Internal Server Error
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Database connection failed",
  "path": "/api/v1/whales"
}
```

### Cryptography-Specific Errors

#### CryptoException
```json
{
  "timestamp": "2024-03-17T03:04:13.501Z",
  "status": 500,
  "error": "Cryptography Error",
  "message": "Invalid signature format",
  "details": {
    "algorithm": "ECDSA",
    "expectedFormat": "hex-encoded signature"
  }
}
```

---

## Pagination System

### Standard Parameters (`GET /api/v1/whales`)

| Parameter | Type | Default | Max | Description |
|-----------|-------|---------|-----|-------------|
| `page` | integer | 0 | — | Page number (0-based) |
| `size` | integer | 20 | 100 | Items per page (values above 100 are clamped to 100) |
| `minEth` | decimal | — | — | Optional minimum **`valueEth`** filter |

Sorting is **fixed** server-side: **`timestamp` descending** (newest first). There is no `sort` / `order` query parameter on this controller.

### Pagination Response Structure

```json
{
  "content": [...],           // Current page items
  "pageable": {
    "pageNumber": 0,        // Current page (0-based)
    "pageSize": 20,          // Items per page
    "totalElements": 150,     // Total items across all pages
    "totalPages": 8,          // Total pages available
    "first": true,            // Is this the first page?
    "last": false,            // Is this the last page?
    "empty": false             // Are there any items at all?
  }
}
```

---

## Rate Limiting

### Current Implementation

Currently no rate limiting is implemented. Consider adding rate limiting for production use:

### Recommended Rate Limits

| Client Type | Requests/Minute | Requests/Hour | Requests/Day |
|-------------|-----------------|----------------|---------------|
| **Anonymous** | 60 | 1000 | 10000 |
| **Authenticated** | 300 | 5000 | 50000 |
| **Premium** | 1000 | 10000 | 100000 |

### Rate Limit Headers

```http
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 299
X-RateLimit-Reset: 1647588420
```

---

## Local Development

### Start Application

```bash
cd lucentflow-api
mvn spring-boot:run -Dspring.profiles.active=local
```

### Test Endpoints

Use Swagger UI for interactive testing:
```
http://localhost:8080/swagger-ui/index.html
```

Or use curl commands:
```bash
# Test health endpoint
curl http://localhost:8080/actuator/health

# Test whale transactions
curl http://localhost:8080/api/v1/whales

# Test with filters
curl http://localhost:8080/api/v1/whales?minEth=100&page=0&size=5
```

---

## Production Considerations

### Security Enhancements

1. **Authentication**: Add API key or JWT-based authentication
2. **Rate Limiting**: Implement Redis-based rate limiting
3. **Caching**: Add Redis caching for frequently accessed data
4. **Monitoring**: Enhanced metrics and alerting
5. **Security**: Add HTTPS, CORS configuration, input validation

### Performance Optimizations

1. **Database Indexing**: Optimize queries for large datasets
2. **Connection Pooling**: Tune HikariCP for production load
3. **Caching Layer**: Implement application-level caching
4. **Async Processing**: Leverage Java 21 Virtual Threads
5. **Load Balancing**: Prepare for horizontal scaling

### Compliance Requirements

1. **Audit Logging**: Complete request/response logging
2. **Data Retention**: Configurable data retention policies
3. **Privacy Compliance**: GDPR-ready data handling
4. **Security Standards**: OWASP compliance for API security
5. **Documentation**: Always keep API docs in sync with implementation

### Forensic query scope

Project-scoped forensic queries are **restricted to the project's watchlist addresses**. When a project has **no watchlist entries**, queries and exports return an **empty result set** (tenant isolation) and set header **`X-LucentFlow-Scope: watchlist-empty`**. Add watchlist addresses before expecting forensic data.

JSON/CSV export is **capped** (SQL `LIMIT`) at `LUCENTFLOW_API_FORENSICS_EXPORT_MAX_ROWS` (default **10000**). Clients may pass `maxRows` to request a smaller slice; the server never exceeds the configured cap. Response headers:

| Header | Meaning |
|--------|---------|
| `X-LucentFlow-Export-Max-Rows` | Applied limit |
| `X-LucentFlow-Export-Matched` | Rows matching filters (before limit) |
| `X-LucentFlow-Export-Truncated` | `true` when matched > max rows |

Use paginated `GET /forensics/events` to walk the rest of the result set.

Topology (`GET /api/v1/forensics/topology/{address}`) is watchlist-gated on the **lookup address** only. Addresses not on the project list return empty edges with `"scope": "watchlist-miss"`. After a hit, inbound/outbound `funding_edges` are the **global** graph — counterparties do not need to be on the same project watchlist. Use **`POST /api/v1/risk/score`** to score an arbitrary address (not watchlist-gated).

### Outbound alerts

| Channel | Scope | Config |
|---------|--------|--------|
| HMAC webhook | **Per project** (one POST per matching project; project URL or global `LUCENTFLOW_WEBHOOK_URL` fallback) | `projects.webhook_url` / `webhook_secret` |
| Discord | **Operator-global** (one message per whale; watchlist labels from all matching projects are merged) | `LUCENTFLOW_DISCORD_WEBHOOK_URL` |
| Telegram | **Operator-global** (same merge as Discord) | `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` |

Webhook delivery waits up to `LUCENTFLOW_WEBHOOK_BULKHEAD_ACQUIRE_TIMEOUT_MS` (default 10000) for a permit (`LUCENTFLOW_WEBHOOK_BULKHEAD_PERMITS`, default 50). Saturation enqueues an in-memory dead-letter (`LUCENTFLOW_WEBHOOK_DEAD_LETTER_CAPACITY`, default 500) and retries on a 500 ms drain. Exhausted attempts or a full queue log **ERROR** and increment the delivery failure counter.

### Auth model & quotas

| Surface | Auth | Limits |
|---------|------|--------|
| `/api/v1/whales`, `/whales/stats`, `/sync-status` | **None** (platform free tier) | IP soft rate limit (`LUCENTFLOW_API_PUBLIC_RATE_LIMIT_PER_MINUTE`, default 60; `0` disables) |
| `/forensics/**`, `/watchlist/**`, `/alert-rules/**`, `/usage/**`, `/risk/**` | `X-Project-Key` | Per-project daily quota (`projects.daily_request_quota`, BUILDER default 2000) + per-minute rate (`LUCENTFLOW_API_RATE_LIMIT_PER_MINUTE`). Watchlist writes honor `projects.watchlist_limit` (BUILDER default 50) via an atomic occupancy UPSERT (`project_watchlist_usage`). |
| `/admin/projects/**` | `X-Admin-Key` | Admin key required |

`LUCENTFLOW_API_DAILY_REQUEST_QUOTA` is only the fallback when a project row has no quota. Daily admits are reserved atomically at request start and refunded when the response is not 2xx. A minute-bucket permit taken before a daily reject is refunded in the same UTC minute. Watchlist creates reserve a slot the same way and refund on unique-constraint races or delete. Exceeding project quotas returns **HTTP 429**.

---

## B2B Project-Scoped APIs (v1.2.0)

All endpoints below require the **`X-Project-Key`** header unless noted.

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/v1/risk/score` | POST | Project | On-demand risk score for an address or tx hash (no watchlist required) |
| `/api/v1/forensics/events` | GET | Project | Paginated forensic event query |
| `/api/v1/forensics/events/export/json` | GET | Project | Streaming JSON export (row-capped) |
| `/api/v1/forensics/events/export/csv` | GET | Project | Streaming CSV export (row-capped) |
| `/api/v1/watchlist` | CRUD | Project | Project-scoped watchlist |
| `/api/v1/alert-rules` | GET/PUT | Project | Alert thresholds and routing rules |
| `/api/v1/usage?days=30` | GET | Project | Daily API request counters |
| `/api/v1/forensics/topology/{address}` | GET | Project | Watchlist-gated lookup; after a hit, global funding edges |
| `/api/v1/oracle/eth-usd` | GET | Public | Cached ETH/USD price |
| `/api/v1/admin/backfill` | POST | Admin | Historical block-range backfill (**indexer process**; API-only replica returns 503) |

### Historical backfill (`POST /api/v1/admin/backfill`)

Does **not** move `sync_status` (ID=1). Requires the indexer runtime. On a K8s split, call the **worker** via `kubectl port-forward deploy/lucentflow-worker 8080:8080` — the API Service has no orchestrator and returns **503**. Monolith (`enable-indexer=true` and `enable-api=true`) accepts the same path on the single process.

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/backfill \
  -H "X-Admin-Key: $LUCENTFLOW_ADMIN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"fromBlock":100,"toBlock":200}'
```

### Risk Score (`POST /api/v1/risk/score`)

Does **not** require the address to be on the project watchlist. Uses a dedicated RPC permit pool (`LUCENTFLOW_API_RISK_SCORE_MAX_CONCURRENT`, default 4) so lookups cannot starve the indexer. Timeout: **504** after `LUCENTFLOW_API_RISK_SCORE_TIMEOUT_MS` (default 8000) with JSON `{"status":504,"error":"Gateway Timeout","message":"..."}`. Saturated pool: **503** with the same JSON shape. Missing `address` and `txHash`: **400**. Successful scores are cached in-process with TTL `LUCENTFLOW_API_RISK_SCORE_CACHE_TTL_MS` (default 60s) and max size `LUCENTFLOW_API_RISK_SCORE_CACHE_MAX_SIZE` (default 256).

```bash
curl -sS -X POST http://localhost:8080/api/v1/risk/score \
  -H "X-Project-Key: demo-project-key-2026" \
  -H "Content-Type: application/json" \
  -d '{"address":"0x6ac359924348dd492a7751af122d781db984b70a"}'
```

| Field | Meaning |
|-------|---------|
| `riskModelVersion` | Frozen model id (`risk-1.0`) |
| `score` | Live clamped 0–100 from `RiskEngine.complete` (same finish step as ingest persist) |
| `rawScore` | Uncapped sum of reason weights |
| `reasons` | Code → points |
| `bytecodeHash` / `cloneCount` | Creation fingerprint + 7-day clone count |
| `fundingHops` / `fundingSource*` / `blacklistedFunding` | Genesis Trace (SQL). Point lookup **always** traces; ingest may skip when score ≤ 40 or catch-up lag is high, so persisted `whale_transactions.risk_score` can be **lower** than this live `score` for the same hash |
| `asOfBlock` / `computedAt` | Audit cursor |
| `coverage` | `indexed` (a whale row was used as context) · `partial` (RPC or genesis only) · `unknown`. `indexed` does **not** mean `score` equals the stored ingest score |

Case fixtures: deployer `0x6ac359924348dd492a7751af122d781db984b70a` (Case #001); bytecode `87192e36234d9184a43f740488a3a0c663e86a192e001cbabde48f000c0a1511` (Case #002).

Admin create/update accepts `plan` (`BUILDER` \| `DESK` \| `PROTOCOL`) and optional quota overrides (`dailyRequestQuota`, `watchlistLimit`).

### Admin APIs

Require **`X-Admin-Key`** (`LUCENTFLOW_ADMIN_API_KEY`). Returns **503** when unset.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/admin/projects` | GET/POST | List or create projects (`webhookSecret` optional; never echoed — `webhookSecretConfigured` flag) |
| `/api/v1/admin/projects/{id}` | GET/PUT | Get or update project (set/clear `webhookSecret` for per-project HMAC) |
| `/api/v1/admin/projects/{id}/rotate-key` | POST | Rotate project API key |
| `/api/v1/admin/projects/{id}/usage` | GET | Usage stats for any project |

### Demo bootstrap

```bash
psql -h localhost -U admin -d lucentflow -f lucentflow-api/src/main/resources/db/demo_setup.sql
```

Demo key: `demo-project-key-2026`

---

## SDK Integration

### JavaScript/TypeScript

```typescript
interface WhaleTransaction {
  id: number;
  hash: string;
  fromAddress: string;
  toAddress: string;
  valueEth: string; // 18-decimal precision string
  valueUsd?: string;
  blockNumber: number;
  timestamp: string; // ISO-8601 format
  transactionType: 'REGULAR_TRANSFER' | 'EXCHANGE' | 'DEFI' | 'CONTRACT_CREATION' | 'FRESH_WHALE';
  whaleCategory: 'WHALE' | 'MEGA_WHALE' | 'GIGA_WHALE' | 'FRESH_WHALE';
  isContractCreation: boolean;
}

// Safe ETH value handling
import { BigNumber } from 'bignumber.js';

const parseEthValue = (valueEth: string): BigNumber => {
  return new BigNumber(valueEth);
};

const formatEthForDisplay = (valueEth: string): string => {
  const ethValue = new BigNumber(valueEth);
  return ethValue.div(new BigNumber('10').pow(18)).toFixed(4);
};
```

### Python

```python
from decimal import Decimal, getcontext

# Set precision for cryptocurrency calculations
getcontext().prec = 18

def parse_eth_value(value_eth: str) -> Decimal:
    """Parse ETH value with 18-decimal precision"""
    return Decimal(value_eth)

def format_eth_for_display(value_eth: str) -> str:
    """Format ETH value for display"""
    eth_value = Decimal(value_eth)
    return str(eth_value / Decimal('10') ** 18)
```

---

*API Documentation maintained for LucentFlow v1.2.0-STABLE with Spring Boot 3.4*
