# LucentFlow API Documentation

## Overview

The LucentFlow API provides endpoints for querying whale transactions and blockchain synchronization status. The API is built with Spring Boot and documented with OpenAPI 3.0 (Swagger).

## Base URL

```
http://localhost:8080/api/v1
```

## Swagger UI

Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui/index.html
```

## Endpoints

### 1. Get Whale Transactions

**Endpoint:** `GET /api/v1/whales`

**Description:** Retrieve paginated list of whale transactions with optional minimum ETH value filter.

**Parameters:**
- `minEth` (optional, query): Minimum ETH value to filter transactions (e.g., 10.0)
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

**Response Format:**
```json
{
  "content": [
    {
      "id": 1,
      "hash": "0x123...",
      "fromAddress": "0xabc...",
      "toAddress": "0xdef...",
      "valueEth": 150.5,
      "blockNumber": 12345678,
      "timestamp": "2024-01-01T12:00:00",

**Precision Note**: The `valueEth` field contains decimal values with up to 18 decimal places. Use high-precision libraries like `BigNumber.js` in frontend applications to handle cryptocurrency values accurately and avoid floating-point precision loss.
      "isContractCreation": false,
      "transactionType": "REGULAR_TRANSFER",
      "fromAddressTag": "UNKNOWN",
      "toAddressTag": "Coinbase Proxy",
      "whaleCategory": "MEGA_WHALE",
      "addressTag": "UNKNOWN",
      "transactionCategory": "EXCHANGE",
      "createdAt": "2024-01-01T12:00:00",
      "updatedAt": "2024-01-01T12:00:00"
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
  "number": 0,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  },
  "empty": false
}
```

### 2. Get Sync Status

**Endpoint:** `GET /api/v1/sync-status`

**Description:** Retrieve current blockchain synchronization status including last scanned block.

**Example Request:**
```bash
curl "http://localhost:8080/api/v1/sync-status"
```

**Response Format:**
```json
{
  "lastScannedBlock": 12345678,
  "createdAt": "2024-01-01T12:00:00",
  "updatedAt": "2024-01-01T12:30:00",
  "syncStatus": "ACTIVE"
}
```

**Pipeline Safety**: The synchronization status includes a safe buffer to handle L2 chain re-organizations. The `lastScannedBlock` may be slightly behind the current chain tip to ensure data consistency during network reorganizations.

**Timestamp Standard**: All timestamp fields follow ISO-8601 UTC format (e.g., `2024-01-01T12:00:00`). Ensure proper timezone handling in client applications.

### 3. Get Whale Statistics

**Endpoint:** `GET /api/v1/whales/stats`

**Description:** Retrieve statistics about whale transactions including total count and largest transaction.

**Example Request:**
```bash
curl "http://localhost:8080/api/v1/whales/stats"
```

**Response Format:**
```json
{
  "totalWhaleTransactions": 150,
  "databaseStatus": "CONNECTED",
  "lastUpdated": 1704110400000,
  "largestWhaleTransaction": {
    "hash": "0xabc...",
    "valueEth": 2500.0,
    "fromAddress": "0xdef...",
    "toAddress": "0x123...",
    "timestamp": "2024-01-01T12:00:00"
  }
}
```

## Whale Categories

- **WHALE**: 10-100 ETH
- **MEGA_WHALE**: 100-1000 ETH
- **GIGA_WHALE**: 1000+ ETH

## Transaction Categories

- **REGULAR**: Standard whale transfers
- **EXCHANGE**: Interactions with exchanges (Coinbase, etc.)
- **DEFI**: DeFi protocol interactions (Uniswap, Aerodrome, etc.)
- **CONTRACT_CREATION**: New smart contract deployments
- **FRESH_WHALE**: High-value transfers from unknown addresses

## Address Tags

Known addresses are tagged with human-readable labels:
- **Coinbase Proxy**: `0x49048044D57e1C23A120ab3913D2258d96af6E56`
- **Uniswap V3 Router**: `0x26213694093010b985442A2338BCe7E690558133`
- **WETH**: `0x4200000000000000000000000000000000000006`
- **BaseSwap Router**: `0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913`
- **Aerodrome Router**: `0x327Df1E0e0B5A90D5A604B2C45B6c9b8F5E3f4B1`

Unknown addresses are formatted as `0x1234...abcd`.

## Error Responses

**400 Bad Request:**
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid parameters provided",
  "path": "/api/v1/whales"
}
```

**500 Internal Server Error:**
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Database connection failed",
  "path": "/api/v1/whales"
}
```

## Rate Limiting

Currently no rate limiting is implemented. Consider adding rate limiting for production use.

## Pagination

All list endpoints support pagination:
- **Default page size**: 20
- **Maximum page size**: 100
- **Sorting**: By timestamp descending (newest first)

## Local Development

### Start the Application

```bash
cd lucentflow-api
mvn spring-boot:run -Dspring.profiles.active=local
```

### Access Swagger UI

Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

### Test Endpoints

Use Swagger UI to test endpoints interactively, or use curl commands provided above.

## Production Considerations

1. **Authentication**: Add API key or JWT authentication
2. **Rate Limiting**: Implement rate limiting to prevent abuse
3. **Caching**: Add Redis caching for frequently accessed data
4. **Monitoring**: Add metrics and health checks
5. **Security**: Add HTTPS, CORS configuration, and input validation

---

*Documentation maintained by @author ArchLucent*
