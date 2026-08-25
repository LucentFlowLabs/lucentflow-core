# Protocol checklist (architect-review)

Source of truth remains `AGENTS.md` and `PROCESS.md` §4. This file is a review index only.

| Id | Check |
|----|--------|
| ID1 | `sync_status` reads/writes `id = 1` only. Progress is monotonic (`GREATEST` or `WHERE last_scanned_block < :n`). No extra checkpoint rows. |
| UPSERT | Whale ingest uses native `ON CONFLICT (hash)`, not naive JPA `save`. |
| PIPE | `TransactionPipe` does not drop; producers block on the bound. |
| RPC-429 | HTTP 429 is pacing/backpressure, not backup-URL failover. |
| SINK-ALERT | `WhaleDatabaseSink` failures propagate. Alerts run only after a successful UPSERT. |
| TENANT | Forensic service fail-closed when `projectId` is null (empty/error), not the global whale table. |
| RUG | `rug_risk_level` is funding-origin. Entity `setRiskScore` must not remap it. |
| ADDR | Address maps store and look up lowercase (`Locale.ROOT`). |
| VT | New I/O work prefers virtual threads, not extra platform pools. |
| ETH-TIME | `BigDecimal` for ETH; `Instant` for timestamps. |
| SCOPE | Smallest change; no drive-by refactors, new modules, or unsolicited markdown. |
| SCHEMA | New schema is `V2__…sql` (or later), not a silent V1 rewrite. |
| AUTH | Do not add Spring Security unless the operator asked. |
| LANG | Code and comments in English; author tag `@author ArchLucent`. |
