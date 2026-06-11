package com.drivewealth.Reconciliation_Engine.breaks;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutoResolutionResult {

    private String runId;
    private int totalRoundingBreaks;
    private int autoResolved;

}
