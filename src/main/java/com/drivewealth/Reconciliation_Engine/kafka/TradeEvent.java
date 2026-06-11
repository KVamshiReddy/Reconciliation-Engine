package com.drivewealth.Reconciliation_Engine.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {

    private String fillId;
    private String userId;
    private String symbol;
    private BigDecimal shares;
    private BigDecimal price;
    private BigDecimal notional;
    private LocalDate tradeDate;

}
