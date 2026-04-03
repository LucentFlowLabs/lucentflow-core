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

If the RPC endpoint is down or times out, `jsonRpc` reports **DOWN** with `rpc` / `error` / `message` details, which typically drives the **overall** status to **DOWN**—useful for catching misconfigured `LUCENTFLOW_CHAIN_RPC_URL` before traffic is admitted.

**Usage:**
```bash
curl http://localhost:8080/actuator/health
```

---

### 2. Blockchain Synchronization Status

#### GET /api/v1/sync-status

**Description:** Retrieve current blockchain synchronization status including last scanned block and pipeline state.

**Response Fields (ID=1 Protocol):**
- `lastScannedBlock`: Latest block number successfully indexed (`0` when not started)
- `syncStatus`: `ACTIVE` when row **id=1** exists in `sync_status`, otherwise `NOT_STARTED`
- `createdAt` / `updatedAt`: Row timestamps from `sync_status` (nullable when not started)

**Example Request:**
```bash
curl "http://localhost:8080/api/v1/sync-status"
```

**Response Format (indexed):**
```json
{
  "lastScannedBlock": 43213473,
  "createdAt": "2024-03-17T03:06:58.769Z",
  "updatedAt": "2024-03-17T03:07:00.483Z",
  "syncStatus": "ACTIVE"
}
```

**Response Format (no sync row yet):**
```json
{
  "lastScannedBlock": 0,
  "createdAt": null,
  "updatedAt": null,
  "syncStatus": "NOT_STARTED"
}
```

**Note:** Chain tip, block lag, and pipeline state are exposed via **Metabase / operational SQL** (see `docs/metabase.md` and `INFRASTRUCTURE.md`), not this minimal JSON contract.

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

- **Storage:** `entity_tags` (see Flyway migrations) holds canonical **address → label** rows used by **`TagOracleService`** in the analyzer.
- **API surfacing:** Resolved tags appear on whale payloads as **`fromAddressTag`**, **`toAddressTag`**, **`addressTag`**, and related fields on **`GET /api/v1/whales`** when the pipeline has enriched the row.
- **Scale:** Full-scale deployments often maintain **hundreds** of institutional labels (700+ is typical for curated forensics datasets). **Row count is deployment-dependent** (Flyway seeds + operator imports); do not assume a fixed catalog size from the API alone.
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

*API Documentation maintained for LucentFlow v1.0.0-RELEASE with Spring Boot 3.4*
