# LucentFlow Core

![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat&logo=java)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?style=flat&logo=spring-boot)
![Web3j](https://img.shields.io/badge/Web3j-4.12.0-blue?style=flat)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?style=flat&logo=postgresql)

> **Clarity in Stream.**
> High-performance Base (L2) network monitoring and asset security auditing engine. Built with Java 21 Virtual Threads & Structured Concurrency.

---

## 🛡️ Project Vision
LucentFlow is designed to bring transparency to the Base Layer 2 ecosystem. By utilizing an industrial-grade pipeline architecture, it monitors large-scale capital movements (Whales) and detects high-risk contract deployments in real-time, providing funding source traceability before "rug" happens.

## 🏗️ Technical Architecture
```mermaid
graph LR
    subgraph "Base Network (L2)"
        RPC[Public/Private RPC]
    end

    subgraph "LucentFlow Engine (Java 21 Virtual Threads)"
        Source[BaseBlockPoller] -->|Block Polling| Pipe[TransactionPipe]
        Pipe -->|Transaction Stream| Worker[WhaleAnalysisWorker]
        Worker -->|Heuristics/Tagging| Sink[WhaleDatabaseSink]
    end

    subgraph "Persistence & Visualization"
        Sink --> DB[(PostgreSQL 16)]
        DB --> Dashboard[Metabase Dashboard]
    end

    API[RESTful Gateway] <--> DB
```

## ⚡ Key Engineering Features
- **Project Loom Integration**: Entire pipeline runs on Virtual Threads, achieving massive I/O throughput with minimal memory footprint.
- **Zero-Loss Guarantee**: Custom-built TransactionPipe utilizes BlockingQueue semantics to ensure no audit data is dropped under network spikes.
- **BIP-44 Path Anchoring**: Optimized address derivation logic achieving 300% higher throughput for batch wallet auditing.
- **Base Oracle Integration**: Precise transaction cost estimation including L1 Data Fees and L2 Execution Fees.

## 🚀 Quick Start (Local Environment)

### Prerequisites
- Docker & Docker Desktop
- JDK 21 (for build)
- Maven 3.9+
- **Reliable Internet**: Required for Maven dependencies (consider proxy in restricted regions)

### Spin up the Infrastructure
```bash
cd lucentflow-deployment/docker
docker-compose up -d
```

### Run the Engine
```bash
mvn clean install -DskipTests
cd lucentflow-api
java -jar target/lucentflow-api-0.1.0-SNAPSHOT.jar
```

Access the interactive API console at: http://localhost:8080/swagger-ui/index.html

## 📚 Documentation
- [API Reference](./API-DOCUMENTATION.md)
- [Infrastructure Setup](./INFRASTRUCTURE.md)
- [Local Development Guide](./LOCAL-DEVELOPMENT.md)

## ⚖️ License
Distributed under the Apache License 2.0. See LICENSE for more information.
