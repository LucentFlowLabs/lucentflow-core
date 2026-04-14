# Case 002 — The 33-Second Pulse: Decrypting an Industrial-Scale Rug Bot on Base L2

> 📢 **Official Publication:** [View on Paragraph](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2)

**Classification:** Threat intelligence · Base L2 · bytecode cloning · cross-chain funding obfuscation  
**Disclosure:** Formal reporting line to [Blockaid](https://blockaid.io/) (Coinbase ecosystem security partner); reference **ticket #1235288**. Public program context: [**Blockaid Threat Intelligence**](https://blockaid.io/threat-intelligence).

> **Local mirror:** version-controlled **sovereign audit log** (text-only). Heavy pulse charts and timeline imagery are **excluded from the repository** by policy; narrative and IOCs below match the [Official Publication](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2).

---

## Summary

This case examines an **industrial-scale deployment factory** on Base L2: a tempo-bound cadence (a **~33-second pulse**) of **cloned bytecode** deployments where operators optimized for **throughput and obfuscation** over bespoke engineering. Funding rails showed **deliberate cross-chain obfuscation**—including ingress patterns consistent with **portfolio and bridge surfaces** (e.g. **Zerion**-class portfolio UX and **Across**-style bridge settlement semantics)—making naive “single-hop” tracing insufficient without **internal-tx–aware** forensics.

> 🖼️ **Visual Evidence:** High-fidelity dashboard captures showing the **~33-second deployment pulse** and **sub-block timestamp / ordering gaps** (including the **≈2-second** inter-create anomaly highlighted in the long-form analysis) are available in the [Official Publication](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2).

---

## Technical Indicators (IOCs)

Searchable signatures for **GitHub global search** and offline SQL:

| Kind | Value |
|------|--------|
| **Bytecode hash** (`whale_transactions.bytecode_hash`) | `87192e36234d9184a43f740488a3a0c663e86a192e001cbabde48f000c0a1511` |

This is the **normalized creation-input fingerprint** used to collapse clone deployments into a single operator template.

---

## Forensic Methodology (LucentFlow)

- **Bytecode fingerprinting** — Match **`bytecode_hash`** to **`creation_bytecode`** semantics (SHA-256 over normalized deploy `input`) so factory clones cannot hide behind fresh addresses.  
- **Temporal anomaly detection** — Detect the **33-second “pulse”** cadence (non-Poisson inter-arrival of `contract_creation` events) and **micro-gap** ordering anomalies between sibling transactions.  
- **Internal-tx origin tracing** — Reconstruct **Zerion / Across** ingress: internal transfers, router calldata, and settlement timing—not only top-level native transfers.

---

## Scripted bytecode cloning

Deployments shared **identical or near-identical creation bytecode hashes**, indicating **template-driven** factory behavior rather than independent projects. LucentFlow’s **bytecode fingerprinting** and **cluster linkage** were used to collapse thousands of surface addresses into a **small operator set** for reporting.

> 🖼️ **Visual Evidence:** High-fidelity **cluster / hash-equality** diagrams tying multiple create2 surfaces to one **bytecode hash** are available in the [Official Publication](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2).

---

## Cross-chain funding obfuscation

**Key insight:** factory operators often **prefund** through bridges and portfolio aggregators to **distance** hot wallets from the eventual deployer. De-cloaking requires mapping **internal transfers**, **router calldata**, and **settlement timing**—not only top-level ETH moves.

---

## Ecosystem references

- [Across Protocol](https://across.to/) — bridge documentation and settlement model.  
- [Zerion](https://zerion.io/) — wallet / portfolio aggregation (ingress pattern context).  
- [Blockaid — Threat Intelligence](https://blockaid.io/threat-intelligence)

---

### Verification

To verify this case locally:

1. Deploy LucentFlow **v1.1.0-STABLE** (see root `README.md` and `docs/LOCAL-DEVELOPMENT.md`).  
2. Sync Base mainnet block range **`[Start_Block]`** to **`[End_Block]`** documented in the [Official Publication](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2) (canonical factory window).  
3. Query PostgreSQL **`whale_transactions`** for **`bytecode_hash = '87192e36234d9184a43f740488a3a0c663e86a192e001cbabde48f000c0a1511'`** (and time-correlated rows) to reproduce the clone cluster described above.

---

*Primary narrative: [Paragraph — Case #002](https://paragraph.com/@archlucent@proton.me/the-33-second-pulse-decrypting-an-industrial-scale-rug-bot-on-base-l2). This repository copy is the **local evidentiary mirror**; visual storytelling remains on Paragraph.*
