# Case 001 — Automated Fraud on Base: A Forensic Breakdown of the “Kriptogame” Rug-Bot

> 📢 **Official Publication:** [View on Paragraph](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot)

**Classification:** Threat intelligence · Base L2 · contract deployment abuse  
**Disclosure:** Formal reporting line to [Blockaid](https://blockaid.io/) (Coinbase ecosystem security partner); reference **ticket #1235288**. Public program context: [**Blockaid Threat Intelligence**](https://blockaid.io/threat-intelligence).

> **Local mirror:** version-controlled **sovereign audit log** (text-only). Heavy dashboard and timeline imagery are **excluded from the repository** by policy; narrative and IOCs below match the [Official Publication](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot).

---

## Summary

This case documents **automated, scripted deployment behavior** on Base where malicious actors attempted **high-frequency contract creation** paired with **deceptive ENS naming** to mimic legitimate gaming or token brands. A subset of deployment transactions **reverted on-chain**—preserving an evidence trail of failed “probe” launches while other paths advanced toward liquidity events.

LucentFlow’s pipeline surfaced **revert-rich deployment bursts**, **bytecode similarity clusters**, and **ENS resolution patterns** inconsistent with organic project launches.

> 🖼️ **Visual Evidence:** High-fidelity dashboard captures showing the **Anti-Rug / risk-score (≈135)** surface and correlated deployment telemetry are available in the [Official Publication](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot).

---

## Technical Indicators (IOCs)

Searchable signatures for **GitHub global search** and offline SQL:

| Kind | Value |
|------|--------|
| **Primary deployer (EOA)** | `0x6ac359924348dd492a7751af122d781db984b70a` |

Correlate this address with `whale_transactions.from_address`, `to_address`, `funding_source_address`, and contract-creation rows ingested by LucentFlow.

---

## Forensic Methodology (LucentFlow)

- **Bytecode fingerprinting** — Cluster contracts by **`bytecode_hash`** (SHA-256 over normalized **creation input**), equivalent to matching **creation bytecode** semantics across clone deployments.  
- **Temporal anomaly detection** — Flag **non-human inter-arrival times** on deployment bursts (scripted cadence vs organic launches), including tight coupling between **reverted** and **successful** creates from the same operator graph.  
- **Internal-tx origin tracing** — Resolve **Zerion / Across-class** ingress (routers, bridges, portfolio surfaces) so seed funding is not misread as a single-hop top-level ETH transfer.

---

## On-chain indicators

- Burst **contract creations** from correlated EOAs with low historical reputation.  
- **Reverted** `eth_getTransactionReceipt` paths indicating intentional throw / guard failures during automated sweeps.  
- **ENS** registrations and primary names chosen for **look-alike** semantics against known brands (homoglyph and namespace squatting patterns).

> 🖼️ **Visual Evidence:** High-fidelity **timeline / receipt** panels showing **revert-heavy** deployment bursts next to **ENS** resolution context are available in the [Official Publication](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot).

---

## ENS and reverted deployments

Forensic value lies in correlating **failed deployments** with **successful** ones from the same operator graph: reverts often encode **budget probes** or **guard checks** before capital is committed. LucentFlow treats these as **first-class signals** in the Anti-Rug lineage—not noise to be discarded.

---

## Ecosystem references

- [Blockaid — Threat Intelligence](https://blockaid.io/threat-intelligence)  
- [Base — Documentation](https://docs.base.org/)

---

### Verification

To verify this case locally:

1. Deploy LucentFlow **v1.1.0-STABLE** (see root `README.md` and `docs/LOCAL-DEVELOPMENT.md`).  
2. Sync Base mainnet block range **`[Start_Block]`** to **`[End_Block]`** documented in the [Official Publication](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot) (canonical burst window).  
3. Query PostgreSQL **`whale_transactions`** (and related analyst outputs) for the deployer **`0x6ac359924348dd492a7751af122d781db984b70a`** and correlated contract hashes; align with **`bytecode_hash`** clusters described in the publication.

---

*Primary narrative: [Paragraph — Case #001](https://paragraph.com/@archlucent@proton.me/automated-fraud-on-base-a-forensic-breakdown-of-the-kriptogame-rug-bot). This repository copy is the **local evidentiary mirror**; visual storytelling remains on Paragraph.*
