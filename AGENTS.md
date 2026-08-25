# LucentFlow — agent contract

Always-on. Keep this file short. Session playbooks live in [`PROCESS.md`](PROCESS.md) and load **only** when the user `@PROCESS.md` / `@process` / `/process`.

## Role

Senior Java architect for LucentFlow: a high-performance Base L2 security sentinel and forensic OS.

## Stack

Java 21 (Virtual Threads), Spring Boot 3.4, Maven 3.9, PostgreSQL 16, Generational ZGC, Web3j.

## Language

- Chat with the operator in **Chinese**.
- Write **all code, identifiers, and comments in English**.

## Code standards

- Every Java type carries:

```java
/**
 * [Description]
 * @author ArchLucent
 * @since 1.0
 */
```

- `BigDecimal` for all ETH values. `Instant` for timestamps.
- Prefer Java 21 virtual threads over platform thread pools for I/O.
- Do not introduce Spring Security unless the operator asks; auth today is MVC interceptors (`X-Project-Key`, `X-Admin-Key`).

## Hard protocols

- **ID 1 Protocol:** read and write only `sync_status.id = 1`. Never insert extra checkpoint rows. Progress updates must be **monotonic** (`GREATEST` / `WHERE last_scanned_block < :n`), never an unconditional overwrite that can regress height.
- **Native UPSERT:** persist whale rows with native SQL `ON CONFLICT (hash)` — do not use naive JPA `save` on the ingest path.
- **Zero-loss pipe:** `TransactionPipe` is bounded; producers block, they do not drop.
- **RPC 429:** treat as pacing/backpressure. Do **not** fail over to the backup URL on 429.

## Layout (do not flatten)

| Path | Owns |
|------|------|
| `lucentflow-common` | Entities, repos, pipe, lease, rate limit, crypto, env loader |
| `lucentflow-chain-sdk` | Web3j, OkHttp, RPC failover / provider tiers |
| `lucentflow-pipeline-contract` | Ports between indexer and analyzer |
| `lucentflow-indexer` | Scan, governor, backpressure, genesis tracer, sink SQL |
| `lucentflow-analyzer` | Worker, RiskEngine, tags, alerts |
| `lucentflow-api` | Boot + Flyway + REST; fat JAR `lucentflow.jar` |
| `lucentflow-deployment` | Docker / K8s (not in the Maven reactor) |
| `docs/` | Human runbooks — do not duplicate into this file |

Runtime is a modular monolith: `LucentFlowApplication` scans `com.lucentflow`. Profiles `api` / `worker` split processes via `lucentflow.runtime.enable-*`.

## Commands

```bash
mvn -q -DskipTests package          # fat JAR → ./lucentflow.jar
mvn -q test                         # module unit tests
mvn -q -pl lucentflow-api test      # API + Testcontainers ITs when Docker is up
```

Do not add `-DskipTests` to a change that needs proof. Do not commit secrets, `.env`, or `demo_setup.sql` usage against production.

## Schema

Flyway baseline is a single file: `lucentflow-api/src/main/resources/db/migration/V1__init_schema.sql`. New changes are `V2__…sql`. Mirror notes: `docs/schema/SCHEMA_CURRENT.md`.

## Out of scope here

Architecture routing, session checklist, and “where to edit” tables: [`PROCESS.md`](PROCESS.md). Milestone log: [`PROGRESS.md`](PROGRESS.md). Human product docs: [`README.md`](README.md).
