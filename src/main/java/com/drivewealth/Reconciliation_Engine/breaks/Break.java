package com.drivewealth.Reconciliation_Engine.breaks;

import com.drivewealth.Reconciliation_Engine.constants.Constants;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "breaks")
public class Break {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "break_id")
    private String breakId;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "internal_position")
    private BigDecimal internalPosition;

    @Column(name = "settlement_position")
    private BigDecimal settlementPosition;

    @Column(name = "difference")
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Constants.BreakCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Constants.BreakStatus status;

    @Column(name = "detected_at",
            columnDefinition = "TIMESTAMP")
    private LocalDate detectedAt;

    @Column(name = "resolved_at",
            columnDefinition = "TIMESTAMP")
    private LocalDate resolvedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @PrePersist
    public void prePersist() {
        this.detectedAt = LocalDate.now();
    }





}
