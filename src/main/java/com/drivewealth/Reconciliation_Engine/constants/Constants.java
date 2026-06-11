package com.drivewealth.Reconciliation_Engine.constants;

public class Constants {

    public enum BreakCategory {

        ROUNDING,
        MISSING_SETTLEMENT,
        MISSING_INTERNAL,
        DUPLICATE,
        CRITICAL

    }

    public enum BreakStatus {

        DETECTED,
        UNDER_REVIEW,
        AUTO_RESOLVED,
        ESCALATED,
        CLOSED

    }

}
