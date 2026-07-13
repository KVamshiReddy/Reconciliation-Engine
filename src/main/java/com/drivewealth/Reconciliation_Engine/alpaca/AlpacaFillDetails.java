package com.drivewealth.Reconciliation_Engine.alpaca;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AlpacaFillDetails {

    private String orderId;
    private String symbol;
    private BigDecimal filledQty;
    private BigDecimal filledAvgPrice;
    private LocalDateTime filledAt;

}
