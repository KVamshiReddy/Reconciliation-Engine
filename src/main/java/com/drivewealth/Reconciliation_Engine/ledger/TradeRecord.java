package com.drivewealth.Reconciliation_Engine.ledger;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "trade_records")
public class TradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "fill_id", nullable = false)
    private String fillId;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "symbol", nullable = false)
    private String symbol;
    @Column(name = "shares", nullable = false, precision = 18, scale = 8)
    private BigDecimal shares;
    @Column(name = "price", nullable = false, precision = 18, scale = 8)
    private BigDecimal price;
    @Column(name = "notional", nullable = false, precision = 18, scale = 8)
    private BigDecimal notional;
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
    @Column(name = "created_at", nullable = false,
            columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
