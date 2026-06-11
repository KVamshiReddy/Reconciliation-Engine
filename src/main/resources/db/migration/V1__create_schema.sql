-- Internal ledger: every fractional trade at full precision
CREATE TABLE trade_records (
    id                BIGSERIAL,
    fill_id           VARCHAR(100) NOT NULL,
    user_id           VARCHAR(100) NOT NULL,
    symbol            VARCHAR(20)  NOT NULL,
    shares            NUMERIC(18, 8) NOT NULL,
    price             NUMERIC(18, 8) NOT NULL,
    notional          NUMERIC(18, 8) NOT NULL,
    trade_date        DATE NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, trade_date),
    UNIQUE (fill_id, trade_date)
) PARTITION BY RANGE (trade_date);

-- Partitions for current month and next month
CREATE TABLE trade_records_2026_06
    PARTITION OF trade_records
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE trade_records_2026_07
    PARTITION OF trade_records
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- Index for fast symbol + date lookups
CREATE INDEX idx_trade_records_symbol_date
    ON trade_records (symbol, trade_date);

CREATE INDEX idx_trade_records_fill_id
    ON trade_records (fill_id);

-- Settlement records: DTCC rounded positions
CREATE TABLE settlement_records (
    id                BIGSERIAL PRIMARY KEY,
    symbol            VARCHAR(20)   NOT NULL,
    settled_shares    NUMERIC(18, 2) NOT NULL,
    settlement_date   DATE NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (symbol, settlement_date)
);

-- Reconciliation runs: tracks every engine execution
CREATE TABLE reconciliation_runs (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              VARCHAR(100) NOT NULL UNIQUE,
    run_date            DATE NOT NULL,
    status              VARCHAR(30) NOT NULL,
    total_symbols       INT DEFAULT 0,
    symbols_processed   INT DEFAULT 0,
    breaks_detected     INT DEFAULT 0,
    auto_resolved       INT DEFAULT 0,
    escalated           INT DEFAULT 0,
    started_at          TIMESTAMP NOT NULL,
    completed_at        TIMESTAMP,
    duration_ms         BIGINT
);

-- Breaks: every discrepancy detected
CREATE TABLE breaks (
    id                  BIGSERIAL PRIMARY KEY,
    break_id            VARCHAR(100) NOT NULL UNIQUE,
    run_id              VARCHAR(100) NOT NULL,
    symbol              VARCHAR(20)  NOT NULL,
    internal_position   NUMERIC(18, 8) NOT NULL,
    settlement_position NUMERIC(18, 2) NOT NULL,
    difference          NUMERIC(18, 8) NOT NULL,
    category            VARCHAR(50)  NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    detected_at         TIMESTAMP NOT NULL,
    resolved_at         TIMESTAMP,
    resolution_note     TEXT
);

-- Audit log: immutable record of every action
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    detail      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);