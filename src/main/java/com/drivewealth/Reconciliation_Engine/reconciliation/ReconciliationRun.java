package com.drivewealth.Reconciliation_Engine.reconciliation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    private String runId;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_symbols")
    private Integer totalSymbols;

    @Column(name = "symbols_processed")
    private Integer symbolsProcessed;

    @Column(name = "breaks_detected")
    private Integer breaksDetected;

    @Column(name = "auto_resolved")
    private Integer autoResolved;

    @Column(name = "escalated")
    private Integer escalated;

    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

}
