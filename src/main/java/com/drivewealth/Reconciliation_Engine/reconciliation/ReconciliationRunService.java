package com.drivewealth.Reconciliation_Engine.reconciliation;

import com.drivewealth.Reconciliation_Engine.breaks.AutoResolutionResult;
import com.drivewealth.Reconciliation_Engine.breaks.BreakDetectionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationRunService {

    private final ReconciliationRunRepository reconciliationRunRepository;
    private final MeterRegistry meterRegistry;

    private Counter breaksDetectedCounter;
    private Counter autoResolvedCounter;
    private Counter escalatedCounter;
    private Timer reconciliationTimer;

    @PostConstruct
    public void initMetrics() {
        breaksDetectedCounter = Counter.builder("reconciliation.breaks.detected")
                .description("Total number of breaks detected")
                .register(meterRegistry);

        autoResolvedCounter = Counter.builder("reconciliation.breaks.auto_resolved")
                .description("Total number of breaks auto resolved")
                .register(meterRegistry);

        escalatedCounter = Counter.builder("reconciliation.breaks.escalated")
                .description("Total number of breaks escalated")
                .register(meterRegistry);

        reconciliationTimer = Timer.builder("reconciliation.run.duration")
                .description("Duration of reconciliation runs in milliseconds")
                .register(meterRegistry);
    }

    @Transactional
    public ReconciliationRun startRun(String runId, LocalDate runDate) {
        if (reconciliationRunRepository
                .existsByRunDateAndStatus(runDate, "COMPLETED")) {
            log.warn("A completed reconciliation run already exists for date: {}", runDate);
        }

        ReconciliationRun run = ReconciliationRun.builder()
                .runId(runId)
                .runDate(runDate)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();

        reconciliationRunRepository.save(run);
        log.info("Reconciliation run started: runId={} date={}", runId, runDate);
        return run;
    }

    @Transactional
    public ReconciliationRun completeRun(
            String runId,
            BreakDetectionResult detectionResult,
            AutoResolutionResult resolutionResult) {

        Optional<ReconciliationRun> runOpt = reconciliationRunRepository
                .findByRunId(runId);

        if (runOpt.isEmpty()) {
            log.error("Run not found: {}", runId);
            throw new RuntimeException("Reconciliation run not found: " + runId);
        }

        ReconciliationRun run = runOpt.get();
        run.setStatus("COMPLETED");
        run.setCompletedAt(LocalDateTime.now());
        run.setBreaksDetected(detectionResult.getTotalBreaks());
        run.setAutoResolved(resolutionResult.getAutoResolved());
        run.setEscalated(detectionResult.getCriticalBreaks()
                + detectionResult.getMissingBreaks());
        run.setDurationMs(detectionResult.getDurationMs());

        reconciliationRunRepository.save(run);

        // Record metrics
        breaksDetectedCounter.increment(detectionResult.getTotalBreaks());
        autoResolvedCounter.increment(resolutionResult.getAutoResolved());
        escalatedCounter.increment(
                detectionResult.getCriticalBreaks() + detectionResult.getMissingBreaks());
        reconciliationTimer.record(
                detectionResult.getDurationMs(), java.util.concurrent.TimeUnit.MILLISECONDS);

        log.info("Reconciliation run completed: runId={} breaks={} resolved={} duration={}ms",
                runId,
                detectionResult.getTotalBreaks(),
                resolutionResult.getAutoResolved(),
                detectionResult.getDurationMs());

        return run;
    }

    @Transactional
    public void failRun(String runId, String reason) {
        reconciliationRunRepository.findByRunId(runId).ifPresent(run -> {
            run.setStatus("FAILED");
            run.setCompletedAt(LocalDateTime.now());
            reconciliationRunRepository.save(run);
            log.error("Reconciliation run failed: runId={} reason={}", runId, reason);
        });
    }

    // Get all runs for a specific date
    public List<ReconciliationRun> getRunsByDate(LocalDate date) {
        return reconciliationRunRepository.findByRunDate(date);
    }

    // Get a specific run
    public Optional<ReconciliationRun> getRun(String runId) {
        return reconciliationRunRepository.findByRunId(runId);
    }

}
