package com.drivewealth.Reconciliation_Engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "reconciliation")
public class ReconciliationProperties {

    private BigDecimal roundingThreshold = new BigDecimal("0.00500000");
    private BigDecimal criticalBreakThreshold = new BigDecimal("0.10000000");
    private boolean autoResolveEnabled = true;
    private String settlementWindowClose = "18:00:00";
    private int threadPoolSize = 20;


}
