# LucentFlow — session playbook

**Load this file to start a work session.** In Cursor: `@PROCESS.md`, or `/process`.

This is the on-demand playbook (routing + checklist). Non-negotiable coding rules stay in [`AGENTS.md`](AGENTS.md) — do not copy them here.

---

## 0. Session start (do this first)

1. Restate the operator’s goal in one sentence. Do not expand scope.
2. Name the **one module** that should change. If two modules must move together, say why.
3. Open the canonical files below **before** editing. Prefer reading over guessing.
4. Chat in Chinese; code and comments in English (`AGENTS.md`).
5. **Surface the card.** The first reply after `/process` / `@PROCESS.md` must include the filled-in **会话清单** below. Do not follow this section only in private. Do not paste the rest of this file. If the operator gave no task yet, still emit the card with 目标/模块 as `待指定`.

Stop and ask only when a product decision is required (auth model, public API contract, schema squash). Implementation details: read the code.

### 会话清单 (opening card)

```text
会话清单
- 目标：<one sentence>
- 模块：<one Maven module; if two, why>
- 先读：<canonical files, or 待任务指定>
- 硬协议：checkpoint id=1 · 原生 UPSERT · 429 不切备份 · 告警仅成功 UPSERT 后
- 本轮不做：扩范围 / 顺手重构 / 未要求的 markdown / 自验收
- 验收门：操作员调 /test → /architect-review → /accept
```

---

## 1. Runtime map

```text
Base RPC  →  Indexer (PipelineOrchestrator / BaseBlockSource)
          →  TransactionPipe (cap 5000)
          →  Analyzer (WhaleAnalysisWorker → RiskEngine → sink → alerts)
          →  PostgreSQL
          →  REST (lucentflow-api)
```

Checkpoint: `sync_status` row **id = 1**. Ingest: native `ON CONFLICT (hash)` UPSERT. Alerts must not fire on a batch whose UPSERT failed.

---

## 2. Routing — “I want to change…” → edit

| I want to change… | Start here |
|-------------------|------------|
| Block scan / checkpoint / backpressure | `lucentflow-indexer/.../pipeline/PipelineOrchestrator.java`, `source/BaseBlockSource.java`, `config/RpcConcurrencyGovernor.java` |
| RPC client / failover / provider tier | `lucentflow-chain-sdk/.../sdk/config/` |
| Pipe, lease, rate limit, entities | `lucentflow-common` |
| Indexer↔analyzer ports | `lucentflow-pipeline-contract` |
| Whale filter / score / tags / webhooks | `lucentflow-analyzer/.../worker/WhaleAnalysisWorker.java`, `service/RiskEngine.java`, `service/AlertService.java` |
| REST, keys, quota, Flyway | `lucentflow-api/.../api/` + `src/main/resources/db/migration/` |
| Compose / K8s / `.env.example` | `lucentflow-deployment/` |
| Human API / schema / local run | `docs/API-DOCUMENTATION.md`, `docs/schema/SCHEMA_CURRENT.md`, `docs/LOCAL-DEVELOPMENT.md` |

Do **not** put conversion logic in `TransactionTransformer` unless you wire it into the live worker path (it is currently test-only). Live conversion is inline in `WhaleAnalysisWorker`.

---

## 3. Doc map (do not merge these into agent files)

| File | Audience | Role |
|------|----------|------|
| `README.md` | Humans | Product + quickstart |
| `AGENTS.md` | Every agent, every turn | Contract |
| `PROCESS.md` | This session, on demand | Playbook |
| `PROGRESS.md` | Humans + agents | Milestone / architecture log |
| `docs/**` | Humans | Runbooks, schema, forensics, changelog |
| `lucentflow-common/.../utils/*.md` | Humans | Historical crypto/refactor notes — not session law |

If a fact lives in code or Flyway, the code wins over `PROGRESS.md`.

---

## 4. Work rules for this session

- Smallest change that satisfies the ask. No drive-by refactors, no new modules, no extra markdown unless asked.
- Tenant forensics: service layer must **fail closed** when `projectId` is null (empty page / error), not return the global whale table.
- `rug_risk_level` is **funding-origin** classification. Do not remap it from `riskScore` in entity setters.
- Address maps: store and look up **lowercase** (`Locale.ROOT`), same as `EntityTag.normalizeAddress`.
- Checkpoint SQL must not regress `last_scanned_block`.
- `WhaleDatabaseSink` failures must propagate; alerts only after a successful UPSERT.
- New schema → new Flyway version. Do not edit V1 after it has been applied anywhere that matters; this repo is greenfield-baseline but keep the habit.
- Tests: add or extend a test next to the bug you touch (`PipelineOrchestrator`, `WhaleDatabaseSink`, `RiskEngine` are currently thin — prefer covering those if you change them).

---

## 5. Verify before finishing

Gates (operator invokes; do not self-accept):

- `/test` — Maven for touched modules
- `/architect-review` — read-only protocol review
- `/review-bugbot` / `/review-security` — optional generic/security
- `/accept` — checklist for the operator; only they may declare acceptance

```bash
mvn -q -pl <module> -am test
```

For API/schema: `lucentflow-api` tests (Testcontainers when Docker is available). For ingest: indexer tests plus any new orchestrator/sink case you added.

UI is not in this repo. If you change REST contracts, point the operator at Swagger (`/swagger-ui/index.html`) and `docs/API-DOCUMENTATION.md`.

---

## 6. Session end

- Summarize what changed and what you did **not** change.
- If `PROGRESS.md` is stale relative to a shipped milestone, update **only** the affected section — do not rewrite the whole log.
- Do not commit unless the operator asked.
