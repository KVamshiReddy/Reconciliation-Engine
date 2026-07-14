# Real-Time Fractional Trade Reconciliation Engine

Live Demo: http://54.90.98.152:8090

> A production-grade reconciliation system targeting the DriveWealth fractional share settlement problem — detecting, categorizing, and resolving position breaks between internal 8-decimal-precision ledger records and 2-decimal DTCC settlement data within the SEC-mandated T+1 window.

**GitHub:** `https://github.com/KVamshiReddy/Reconciliation-Engine/` &nbsp;|&nbsp; **Stack:** Java 21 · Spring Boot 3.3 · Kafka · PostgreSQL 16 · Resilience4j · Micrometer

---

## The Problem

DriveWealth operates an omnibus brokerage infrastructure powering fractional share trading for platforms like Revolut, Stake, and MoneyLion. Internally, fractional positions are tracked at **8 decimal precision** (e.g. `2.82036185` shares of AAPL). When those positions are submitted to DTCC for T+1 settlement, DTCC rounds to **2 decimal places** (`2.82` shares).

The delta — `0.00036185` shares — is a **reconciliation break**.

Across millions of daily trades this produces thousands of breaks that must be:
- Detected against the settlement record
- Categorized by severity (rounding artifact vs genuine discrepancy)
- Auto-resolved or escalated to the operations team
- Documented with a complete audit trail for regulatory compliance

All of this must happen within a **2-hour window** after T+1 settlement closes, per the [SEC T+1 Final Rule (May 2024)](https://www.sec.gov/rules/final/2023/33-11386.pdf).

This engine solves that problem.

---

## Architecture Overview

```
POST /api/trades/simulate/batch
        │
        ▼
TradeEventProducer ──► trades_raw (Kafka topic)
                                │
                                ▼
                     TradeEventConsumer
                                │
                    @Retry + @CircuitBreaker
                                │
                                ▼
                        LedgerService
                    idempotency check (fillId + tradeDate)
                                │
                                ▼
                   trade_records (PostgreSQL 16)
                   PARTITION BY RANGE(trade_date)
                   shares NUMERIC(18,8)
                                │
                                ▼
                    SettlementSimulator
                    aggregates by symbol
                    rounds to 2dp HALF_UP
                                │
                                ▼
                  settlement_records (PostgreSQL)
                  settled_shares NUMERIC(18,2)
                                │
                   ┌────────────┘
                   ▼
        BreakDetectionService
        CompletableFuture.supplyAsync per symbol
        180-thread pool (18 cores × 10)
        compare internal(8dp) vs settled(2dp)
                   │
          ┌────────┴────────┐
          ▼                 ▼
     ROUNDING          CRITICAL / MISSING
     diff ≤ 0.005      diff > 0.10
          │                 │
          ▼                 ▼
    AUTO_RESOLVED       ESCALATED
    + AuditLog entry    + AuditLog entry
          │
          ▼
  ReconciliationRunService
  records metrics → Prometheus
```

---

## Why These Technology Choices

| Decision | Rationale |
|---|---|
| **Kafka** | Decouples trade simulation from ledger persistence. Enables replay, backpressure, and exactly-once semantics via consumer offset management. |
| **PostgreSQL table partitioning** | trade_records partitioned monthly by trade_date. Queries on a specific date scan only that partition — critical for T+1 window performance at scale. |
| **BigDecimal throughout** | IEEE 754 floating point cannot represent 0.1 exactly. Financial calculations require exact decimal arithmetic. `double` is never used for share quantities or prices. |
| **CompletableFuture thread pool** | Break detection per symbol is I/O-bound and fully independent. Parallel execution across 180 threads reduces 10-symbol detection from ~1100ms to ~110ms. |
| **Resilience4j @Retry + @CircuitBreaker** | Database outages are transient. Retry with exponential backoff handles brief blips. Circuit breaker prevents cascading failure when PostgreSQL is unavailable — downstream Kafka consumer stays healthy. |
| **Feature-based package structure** | All code for a feature (entity, repository, service, DTO) lives in one package. Easier to navigate and modify without hunting across technical layers. |
| **Flyway migrations** | Schema changes are versioned, reproducible, and auditable. `ddl-auto: validate` ensures the running app always matches the declared schema. |

---

## Core Components

### Trade Ingestion
- **`TradeController`** — exposes `POST /api/trades/simulate/batch?count=N`. Generates realistic fractional trades across 10 symbols (AAPL, TSLA, NVDA, AMD, GOOGL, MSFT, META, AMZN, NFLX, INTC).
- **`TradeEventProducer`** — publishes `TradeEvent` JSON to the `trades_raw` Kafka topic via `KafkaTemplate`.
- **`TradeEventConsumer`** — `@KafkaListener` with `enable-auto-commit=false`. Deserializes events and delegates to `LedgerService`. Logs partition and offset for every message.
- **`LedgerService`** — idempotency guard: `existsByFillIdAndTradeDate` before every write. `@Retry(name="ledgerService")` with exponential backoff. `@CircuitBreaker` with `processTradeEventFallback` on repeated failure.

### Settlement Simulation
- **`SettlementSimulator`** — reads `trade_records` for a given date, groups by symbol, sums 8dp shares, rounds to 2dp using `RoundingMode.HALF_UP`, writes to `settlement_records`. The rounding is the deliberate source of all reconciliation breaks.

### Break Detection
- **`BreakDetectionService`** — for each symbol on the trade date, submits `CompletableFuture.supplyAsync(task, reconciliationExecutor)`. Tasks run in parallel on the 180-thread pool. Each task:
  1. Reads internal position from `trade_records` (8dp)
  2. Reads settled position from `settlement_records` (2dp)
  3. Computes `difference = internal.subtract(settled)`
  4. Categorizes the break
  5. Returns a `Break` entity

  All futures join. `.exceptionally()` on each future means one failed symbol returns an empty list while others continue. Results are batch-saved.

### Break Categories

| Category | Condition | Resolution |
|---|---|---|
| `ROUNDING` | `diff.abs() ≤ 0.005` | Auto-resolved — within DTCC tolerance |
| `CRITICAL` | `diff.abs() > 0.10` | Escalated — requires operations team investigation |
| `MISSING_SETTLEMENT` | Internal record exists, no DTCC record | Escalated — possible failed settlement submission |
| `MISSING_INTERNAL` | DTCC record exists, no internal ledger | Escalated — possible lost trade event |

### Break Lifecycle

```
DETECTED
    │
    ├─── ROUNDING ──► AUTO_RESOLVED ──► (closed, audit written)
    │
    └─── CRITICAL / MISSING ──► ESCALATED ──► UNDER_REVIEW ──► CLOSED
```

Every state transition writes an immutable `audit_log` entry with timestamp, action, and detail. This is the regulatory paper trail.

### Reconciliation Run Tracking
- **`ReconciliationRunService`** — every engine execution creates a `reconciliation_runs` record. Status transitions: `RUNNING → COMPLETED` on success, `RUNNING → FAILED` on any exception. Stores `breaksDetected`, `autoResolved`, `escalated`, and `durationMs`.

---

## Database Schema

```sql
-- Internal ledger — 8dp precision, partitioned by trade date
CREATE TABLE trade_records (
    id          BIGSERIAL,
    fill_id     VARCHAR(50)    NOT NULL,
    symbol      VARCHAR(10)    NOT NULL,
    shares      NUMERIC(18,8)  NOT NULL,
    price       NUMERIC(18,8)  NOT NULL,
    notional    NUMERIC(18,8)  NOT NULL,
    trade_date  DATE           NOT NULL,
    PRIMARY KEY (id, trade_date),
    UNIQUE (fill_id, trade_date)
) PARTITION BY RANGE (trade_date);

-- Monthly partitions
CREATE TABLE trade_records_2026_06
    PARTITION OF trade_records
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- DTCC settlement records — 2dp precision
CREATE TABLE settlement_records (
    id               BIGSERIAL PRIMARY KEY,
    symbol           VARCHAR(10)   NOT NULL,
    settled_shares   NUMERIC(18,2) NOT NULL,
    settlement_date  DATE          NOT NULL,
    UNIQUE (symbol, settlement_date)
);

-- Break registry
CREATE TABLE breaks (
    id                   BIGSERIAL PRIMARY KEY,
    break_id             VARCHAR(50)    NOT NULL UNIQUE,
    run_id               VARCHAR(50)    NOT NULL,
    symbol               VARCHAR(10)    NOT NULL,
    internal_position    NUMERIC(18,8),
    settlement_position  NUMERIC(18,2),
    difference           NUMERIC(18,8),
    category             VARCHAR(30)    NOT NULL,
    status               VARCHAR(30)    NOT NULL,
    resolved_at          TIMESTAMP,
    resolution_note      TEXT,
    created_at           TIMESTAMP      NOT NULL
);

-- Run history
CREATE TABLE reconciliation_runs (
    id               BIGSERIAL PRIMARY KEY,
    run_id           VARCHAR(50)  NOT NULL UNIQUE,
    run_date         DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    breaks_detected  INTEGER,
    auto_resolved    INTEGER,
    escalated        INTEGER,
    started_at       TIMESTAMP    NOT NULL,
    completed_at     TIMESTAMP,
    duration_ms      BIGINT
);

-- Immutable audit trail
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    VARCHAR(50)  NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    detail       TEXT,
    created_at   TIMESTAMP    NOT NULL
);
```

---

## Resilience Design

### Retry with Exponential Backoff
Applied to `LedgerService.processTradeEvent` and `SettlementSimulator.generateSettlementRecords`.

```
Attempt 1 fails → wait 1s
Attempt 2 fails → wait 2s  (multiplier: 2)
Attempt 3 fails → fallback fires
```

### Circuit Breaker
Applied to `LedgerService`. Sliding window of 10 calls. Opens at 50% failure rate.

```
CLOSED → normal operation, all calls go through
OPEN   → all calls immediately return fallback (no DB hit)
         waits 30s before testing recovery
HALF-OPEN → 3 test calls. Success → CLOSED. Failure → OPEN.
```

**Why this matters:** Without the circuit breaker, every trade event during a PostgreSQL outage waits 30 seconds for HikariCP connection timeout. With the circuit breaker, after the first few failures, subsequent trades fail instantly — the Kafka consumer drains the backlog at full speed when the database recovers.

---

## Observability

Custom business metrics exposed at `/actuator/prometheus`:

| Metric | Type | Description |
|---|---|---|
| `reconciliation_breaks_detected_total` | Counter | Total breaks detected across all runs |
| `reconciliation_breaks_auto_resolved_total` | Counter | Total rounding breaks auto-resolved |
| `reconciliation_breaks_escalated_total` | Counter | Total critical/missing breaks escalated |
| `reconciliation_run_duration_seconds` | Timer | End-to-end reconciliation run duration |

Plus auto-exposed JVM metrics (heap, GC, threads), HTTP request metrics, and HikariCP connection pool metrics via Spring Boot Actuator.

---

## Performance

| Metric | Result | Notes |
|---|---|---|
| Break detection — 10 symbols | **110ms** | Parallel execution on 18-core machine (180-thread pool) |
| Thread pool size | **180 threads** | `availableProcessors() × 10` — I/O-bound workload |
| Throughput load test | **Pending** | Full benchmark in progress |
| Idempotency check | O(1) | Unique index on `(fill_id, trade_date)` |

The thread pool formula `cores × 10` is appropriate for I/O-bound workloads because threads spend most of their time waiting for database responses. More threads than cores allows the CPU to stay busy while threads are blocked on I/O.

---

## API Reference

### Trade Simulation
```
POST /api/trades/simulate              — simulate a single trade
POST /api/trades/simulate/batch?count=N — simulate N trades across 10 symbols
```

### Settlement
```
POST /api/settlement/generate?date=YYYY-MM-DD  — generate settlement records for date
GET  /api/settlement/{symbol}?date=YYYY-MM-DD  — get settlement record for symbol
```

### Reconciliation
```
POST  /api/reconciliation/run?date=YYYY-MM-DD         — run full reconciliation (detect + resolve)
GET   /api/reconciliation/runs?date=YYYY-MM-DD        — list all runs for date
GET   /api/reconciliation/runs/{runId}                — get specific run
GET   /api/reconciliation/breaks                      — all breaks
GET   /api/reconciliation/breaks/open                 — breaks needing attention
GET   /api/reconciliation/breaks/status/{status}      — breaks by status
GET   /api/reconciliation/breaks/symbol/{symbol}      — breaks by symbol
GET   /api/reconciliation/breaks/{breakId}/audit      — full audit trail for a break
PATCH /api/reconciliation/breaks/{breakId}/close?resolutionNote=... — manually close a break
POST  /api/reconciliation/breaks/{runId}/auto-resolve — trigger auto-resolution for a run
```

---

## Running Locally

### Prerequisites
- Java 21
- Docker Desktop
- Maven 3.9+

### Start infrastructure
```bash
docker-compose up -d
```

This starts:
- PostgreSQL 16 on port `5433`
- Kafka on port `9092`
- Zookeeper on port `2181`
- Kafka UI on port `8081` (browse topics at `localhost:8081`)

### Run the application
```bash
mvn spring-boot:run
```

Flyway automatically runs `V1__create_schema.sql` on startup creating all tables and partitions.

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Run the full reconciliation flow
```bash
# 1. Generate trades
curl -X POST "http://localhost:8080/api/trades/simulate/batch?count=20"

# 2. Generate settlement records
curl -X POST "http://localhost:8080/api/settlement/generate"

# 3. Run reconciliation (detect + auto-resolve)
curl -X POST "http://localhost:8080/api/reconciliation/run"

# 4. View metrics
curl http://localhost:8080/actuator/prometheus | grep reconciliation
```

### Run tests
```bash
mvn test
```

---

## Configuration

Key properties in `application.yml`:

```yaml
reconciliation:
  rounding-threshold: 0.00500000      # diff ≤ this → ROUNDING (auto-resolvable)
  critical-break-threshold: 0.10000000 # diff > this → CRITICAL (escalate)
  auto-resolve-enabled: true           # feature flag to disable auto resolution
  thread-pool-size: 0                  # 0 = dynamic (cores × 10), non-zero = fixed

resilience4j:
  retry:
    instances:
      ledgerService:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
  circuitbreaker:
    instances:
      ledgerService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

---

## Project Structure

```
src/main/java/com/drivewealth/Reconciliation_Engine/
├── api/                    # ReconciliationController, TradeController, SettlementController
├── audit/                  # AuditLog entity + AuditLogRepository
├── breaks/                 # Break entity, BreakRepository, BreakDetectionService,
│                           # BreakService, BreakDetectionResult, AutoResolutionResult,
│                           # ReconciliationRunResult
├── config/                 # KafkaConfig, ThreadPoolConfig, ReconciliationProperties
├── constants/              # Constants (BreakCategory, BreakStatus enums)
├── kafka/                  # TradeEvent, TradeEventProducer, TradeEventConsumer
├── ledger/                 # TradeRecord, TradeRecordRepository, LedgerService
├── reconciliation/         # ReconciliationRun, ReconciliationRunRepository,
│                           # ReconciliationRunService
└── settlement/             # SettlementRecord, SettlementRecordRepository,
                            # SettlementSimulator, SettlementResult

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_schema.sql

docker-compose.yml
```

---

## Domain Context

This project targets the DriveWealth SDE 2 role. DriveWealth operates as a B2B brokerage-as-a-service infrastructure provider — it powers the trading functionality inside consumer apps via API, never appearing directly to end users.

**The fractional share settlement problem in production:**

DriveWealth holds securities in omnibus accounts at DTCC. When a user buys `$10 of AAPL` through Revolut, DriveWealth's internal ledger records `0.05287224 shares` (8dp). DTCC settles the omnibus position at `0.05 shares` (2dp). The `0.00287224` difference flows into DriveWealth's firm account as a rounding adjustment.

Across millions of daily trades these rounding differences must be systematically detected, categorized, and documented. Differences beyond the rounding tolerance indicate genuine errors — failed trade events, corrupted settlement records, or system outages — that require immediate investigation.

This engine automates that detection and resolution process.

---

## Planned Enhancements

- **Slack alerts** — fire webhook when `CRITICAL` break detected, with symbol, difference, and runId
- **Alpaca Paper Trading integration** — replace simulated trades with real market fills during NYSE hours
- **T+1 window deadline alerts** — escalate unresolved breaks approaching the 6pm settlement close
- **Grafana dashboard** — pre-built dashboard JSON for the four custom Prometheus metrics
- **Dead letter queue** — route failed Kafka messages to a DLQ for manual replay

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Message broker | Apache Kafka 3.9 (Docker) |
| Database | PostgreSQL 16 with table partitioning |
| ORM | Hibernate 6 / Spring Data JPA |
| Schema migrations | Flyway 11 |
| Resilience | Resilience4j 2.x (Retry + CircuitBreaker) |
| Metrics | Micrometer + Prometheus |
| API docs | SpringDoc OpenAPI / Swagger UI |
| Connection pool | HikariCP |
| Build | Maven 3.9 |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5 + Mockito + Testcontainers |
| Deployment | AWS EC2 + RDS PostgreSQL + Amazon MSK |
