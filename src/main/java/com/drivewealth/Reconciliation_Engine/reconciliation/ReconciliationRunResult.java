package com.drivewealth.Reconciliation_Engine.reconciliation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReconciliationRunResult{

    private String runId;
    private LocalDate tradeDate;
    private int totalBreaks;
    private int roundingBreaks;
    private int criticalBreaks;
    private int missingBreaks;
    private int autoResolved;
    private long durationMs;

}
