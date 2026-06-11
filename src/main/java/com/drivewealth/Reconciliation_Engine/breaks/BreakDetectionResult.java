package com.drivewealth.Reconciliation_Engine.breaks;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class BreakDetectionResult {
    private String runId;
    private LocalDate tradeDate;
    private int totalBreaks;
    private int roundingBreaks;
    private int criticalBreaks;
    private int missingBreaks;
    private long durationMs;
}