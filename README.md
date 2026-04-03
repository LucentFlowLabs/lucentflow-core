<!--
  LucentFlow project overview — v1.1.0-STABLE
  @author ArchLucent
  @since 1.1
-->

```text
  __    _                            ______              
 / /   (_)___  _____________  ____ _/ ____/___  _____    
/ /   / / __ \/ ___/ ___/ _ \/ __ `/ /_  / __ \/ ___/    
/ /___/ / /_/ / /__/ /  /  __/ /_/ / __/ / /_/ / /__      
/_____/_/ .___/\___/_/   \___/\__, /_/    \____/\___/     
       /_/                  /____/                        
```

# LucentFlow · v1.1.0-STABLE

**High-Performance Sovereign Asset Security & Forensic OS for Base L2**

![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat&logo=java)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?style=flat&logo=spring-boot)
![Base L2](https://img.shields.io/badge/Base-L2-blue?style=flat)
![ZGC](https://img.shields.io/badge/GC-ZGC%20Generational-darkgreen?style=flat)
![Virtual Threads](https://img.shields.io/badge/Threads-Virtual%20(Loom)-purple?style=flat)
![Version](https://img.shields.io/badge/Version-1.1.0--STABLE-blue?style=flat)

> **Sovereign infrastructure:** you own the stack, the keys, and the audit trail. LucentFlow is built for **resilience** under RPC pressure, **data sovereignty** on your hardware, and **high-throughput** forensic analysis—without sacrificing cryptographic rigor.

---

## Why LucentFlow

LucentFlow is an industrial-grade sentinel for **Base L2**: it monitors whale-scale flows, scores creator risk, and traces funding origins with **recursive, evidence-grade forensics**. Version **1.1.0-STABLE** introduces an **Adaptive Environment Sensing Engine**—the runtime discovers configuration and proxies automatically so operators can ship faster with fewer footguns.

---

## Core Value Proposition (v1.1.0)

| Capability | What it delivers |
|------------|------------------|
| **Adaptive RPC pacing** | Intelligent behavior across **PROFESSIONAL** endpoints (Alchemy, QuickNode, Infura, BlastAPI, Ankr, …) and **PUBLIC** infrastructure (`mainnet.base.org`). Official public RPC uses **convention-over-configuration** safe defaults; non-official URLs unlock **optional** `.env` tuning. |
| **Zero-config CLI** | A **mirrored fat JAR** at the repository root (`lucentflow.jar`) after `mvn package`, plus **multi-path `.env` discovery**—optimized for `java -jar` from the project root without a wall of `-D` flags. |
| **Deep genesis trace** | **Three-layer** recursive funding analysis toward **nonce-zero** origins—**Anti-Rug 2.0** lineage: mixers, suspicious deployers, and seed funding reputation are surfaced as first-class signals. |
| **Loom-powered indexer** | A **non-blocking** ingestion pipeline built on **Java 21 Virtual Threads**—parallel block work with bounded RPC fairness and adaptive backpressure. |

---

## The “Hardcore CLI” Quickstart

Build once, run from the repo root. The loader merges `.env` files in **priority order** (first wins on duplicate keys) and applies **profile** and **proxy** intelligence before Spring Boot starts.

```bash
# Build & mirror fat JAR to ./lucentflow.jar
mvn clean package

# Configure (automatic .env discovery — copy template to project root)
cp lucentflow-deployment/docker/.env.example .env

# Run (adaptive proxy & profile sensing)
java -jar lucentflow.jar
```

**Requirements:** `POSTGRES_PASSWORD` must be set (environment or merged `.env` property) before the JVM exits—this is intentional for **sovereign** deployments.

---

## Environment & Proxy Intelligence

### Smart path discovery

The **Adaptive Environment Loader** searches for `.env` files relative to **`user.dir`** (typically your working directory), in order:

1. **`./.env`** — operator overrides at the repository root  
2. **`./lucentflow-deployment/docker/.env`** — standard team template  
3. **`../lucentflow-deployment/docker/.env`** — same template when running from a nested module directory  

Missing files are **ignored**; existing OS environment variables and JVM system properties are **not overwritten**—Kubernetes and Docker-injected secrets remain authoritative.

### Transparent proxy mapping

When present, **`PROXY_HOST`** and **`PROXY_PORT`** from `.env` are applied to **standard JVM HTTP(S) proxy system properties** (`http.proxyHost`, `https.proxyHost`, …) so Spring, OkHttp, and the JDK stack share one consistent view.

**Local vs Docker:**  
If the active profile is **`local`** and **`PROXY_HOST`** is `host.docker.internal` (a Docker Desktop convention), the engine **rewrites** it to **`127.0.0.1`** for bare-metal CLI runs—while container deployments can keep the original hostname when appropriate.

---

## Professional pacing (optional tuning)

When **`LUCENTFLOW_CHAIN_RPC_URL`** points to a **non-official** host (not `mainnet.base.org`), you may tune throughput for paid tiers. **Examples** (see `.env.example` for the full contract):

| Variable | Role |
|----------|------|
| `LUCENTFLOW_INDEXER_MAX_BATCH_SIZE` | Upper bound on block span per scan cycle (orchestrator still respects provider chunk caps). |
| `LUCENTFLOW_INDEXER_MAX_CONCURRENCY` | Baseline RPC semaphore permits for parallel block work. |
| `LUCENTFLOW_CHAIN_PROFESSIONAL_INTER_BATCH_SLEEP_MS` | Breathing room between checkpoint chunks on PROFESSIONAL URLs. |

**Official public RPC** (`mainnet.base.org`) uses **fixed safe defaults** (batch, concurrency, interval) to protect shared infrastructure—**no accidental** `.env` hammering.

---

## Architecture blueprint

- **Generational ZGC** — JVM tuned for **sub-millisecond** pause targets on long-lived indexer + analyzer workloads; generational ZGC reduces young-gen churn for streaming pipelines.  
- **Virtual Threads (Java 21)** — I/O-bound RPC and async analysis scale with **structured concurrency** patterns without thread-pool explosion.  
- **TCV (Triple Cross-Validation) for private keys** — cryptographic primitives are validated through **three independent proofs**: BIP-39 standard vectors, **sign → recover → address** loopback, and **clean-room** Keccak-256 address derivation alongside library paths—so **sovereign** key handling stays auditable.  
- **JSON-RPC resilience** — OkHttp-backed failover to **`LUCENTFLOW_CHAIN_RPC_BACKUP_URL`** on transport failures (HTTP **429** is **not** treated as failover—rate limits are handled by pacing and backoff, preserving **sovereign** control over flapping).

---

## Full-stack & Docker deployment

For **Metabase**, **pgAdmin**, and **fully containerized** stacks, use the compose bundle under `lucentflow-deployment/docker/`. Health checks and operator runbooks live in **`docs/`** (e.g. `docs/metabase.md`, `docs/INFRASTRUCTURE.md`).

```bash
cd lucentflow-deployment/docker
docker compose up --build -d
curl http://localhost:8080/actuator/health
```

---

## Observability

| Surface | URL |
|--------|-----|
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| **Metabase** | [http://localhost:3000](http://localhost:3000) |
| **pgAdmin** | [http://localhost:5050](http://localhost:5050) |

---

## Diagrams (optional)

<p align="center">
  <img src="docs/images/dashboard_hero.png" width="800" alt="Whale ecology dashboard">
  <br>
  <em>Figure 1: Real-time whale ecology & activity patterns on Base.</em>
</p>

<p align="center">
  <img src="docs/images/dockerps.png" width="800" alt="Docker operational status">
  <br>
  <em>Figure 2: Local cluster healthy state (Docker).</em>
</p>

---

## License

**Apache License 2.0** — built for the Base ecosystem and for teams who demand **sovereign**, **verifiable** security infrastructure.
