package com.drivewealth.Reconciliation_Engine.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {

    // Get settlement record for a specific symbol on a specific date
    Optional<SettlementRecord> findBySymbolAndSettlementDate(
            String symbol, LocalDate settlementDate
    );

    // Get all settlement records for a specific date
    List<SettlementRecord> findBySettlementDate(LocalDate settlementDate);

    // Check if settlement already exists for this symbol and date
    boolean existsBySymbolAndSettlementDate(
            String symbol, LocalDate settlementDate
    );
}
