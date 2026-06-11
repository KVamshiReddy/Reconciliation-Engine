package com.drivewealth.Reconciliation_Engine.settlement;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class SettlementResult {
    private LocalDate settlementDate;
    private int symbolsProcessed;
    private int symbolsSkipped;
    private String message;

}
