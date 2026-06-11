package com.drivewealth.Reconciliation_Engine.reconciliation.api;

import com.drivewealth.Reconciliation_Engine.audit.AuditLog;
import com.drivewealth.Reconciliation_Engine.breaks.*;
import com.drivewealth.Reconciliation_Engine.constants.Constants;
import com.drivewealth.Reconciliation_Engine.reconciliation.ReconciliationRun;
import com.drivewealth.Reconciliation_Engine.reconciliation.ReconciliationRunResult;
import com.drivewealth.Reconciliation_Engine.reconciliation.ReconciliationRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.BinaryOperator;

@RestController
@RequestMapping("/api/reconciliation")
@Slf4j
@RequiredArgsConstructor
public class ReconciliationController {

    private final BreakDetectionService breakDetectionService;
    private final BreakRepository breakRepository;
    private final BreakService breakService;
    private final ReconciliationRunService reconciliationRunService;

    @PostMapping("/run")
    public ResponseEntity<?> runReconciliation(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate date
            ) {
        LocalDate tradeDate = date != null ? date : LocalDate.now();
        String runId = "RUN-" + UUID.randomUUID().toString().substring(0,8);
        log.info("Reconciliation run started : runId = {} and date = {}",runId, date);

        reconciliationRunService.startRun(runId, tradeDate);

        try{
            BreakDetectionResult result = breakDetectionService.detectBreaks(runId, tradeDate);

            AutoResolutionResult resolutionResult = breakService.startAutoResolving(result.getRunId());

            ReconciliationRun completedRun = reconciliationRunService.completeRun(runId, result, resolutionResult);

            ReconciliationRunResult finalResult = ReconciliationRunResult
                    .builder()
                    .tradeDate(result.getTradeDate())
                    .totalBreaks(result.getTotalBreaks())
                    .criticalBreaks(result.getCriticalBreaks())
                    .missingBreaks(result.getMissingBreaks())
                    .roundingBreaks(result.getRoundingBreaks())
                    .durationMs(completedRun.getDurationMs())
                    .autoResolved(resolutionResult.getAutoResolved())
                    .runId(runId)
                    .build();

            return ResponseEntity.ok(finalResult);
        } catch (Exception e) {
            reconciliationRunService.failRun(runId, e.getMessage());
            log.error("Reconciliation run failed: runId={}", runId, e);
            return ResponseEntity.internalServerError()
                    .body("Reconciliation run failed: " + e.getMessage());
        }

    }

    @GetMapping("/breaks")
    public ResponseEntity<List<Break>> getAllBreaks() {
        return ResponseEntity.ok(breakRepository.findAll());
    }

    @GetMapping("/breaks/status/{status}")
    public ResponseEntity<List<Break>> getBreaksByStatus(
            @PathVariable Constants.BreakStatus status)
    {
        return ResponseEntity.ok(breakRepository.findByStatus(status));
    }

    @GetMapping("/breaks/symbol/{symbol}")
    public ResponseEntity<List<Break>> getBreaksBySymbol(
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(breakRepository.findBySymbol(symbol));
    }

    @PostMapping("/breaks/{runId}/auto-resolve")
    public ResponseEntity<AutoResolutionResult> startAutoResolving(
            @PathVariable String runId) {
        log.info("Auto Resolution Requested for run ID : {} ", runId);
        AutoResolutionResult result = breakService.startAutoResolving(runId);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/breaks/{breakId}/close")
    public ResponseEntity<Break> closeBreakManual(
            @PathVariable String breakId,
            @RequestParam String note) {
        log.info("Manual Close Initiated for break ID : {}", breakId);
        return breakService.closeBreakManual(breakId, note).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/breaks/open")
    public ResponseEntity<List<Break>> getOpenBreaks() {
        List<Break> breaks = breakService.getOpenBreaks();
        return ResponseEntity.ok(breaks);
    }

    @GetMapping("/break/{breakId}/audit")
    public ResponseEntity<List<AuditLog>> getBreakAuditTrail(@PathVariable String breakId) {
        List<AuditLog> logs = breakService.getBreakAuditTrail(breakId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/runs")
    public ResponseEntity<List<ReconciliationRun>> getRunsByDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(reconciliationRunService.getRunsByDate(queryDate));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(@PathVariable String runId) {
        return reconciliationRunService.getRun(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
