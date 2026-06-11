package com.drivewealth.Reconciliation_Engine.settlement;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "symbol", nullable = false)
    private String symbol;
    @Column(name = "settled_shares", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledShares;
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }



}
