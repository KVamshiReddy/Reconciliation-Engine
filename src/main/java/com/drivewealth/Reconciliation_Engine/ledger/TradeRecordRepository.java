package com.drivewealth.Reconciliation_Engine.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {

    boolean existsByFillIdAndTradeDate(String fillId, LocalDate tradeDate);

    // Get all trades for a specific symbol on a specific date
    List<TradeRecord> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    // Get total shares for a symbol on a specific date (for reconciliation)
    @Query("SELECT COALESCE(SUM(t.shares), 0) FROM TradeRecord t " +
            "WHERE t.symbol = :symbol AND t.tradeDate = :tradeDate")
    BigDecimal sumSharesBySymbolAndTradeDate(
            @Param("symbol") String symbol,
            @Param("tradeDate") LocalDate tradeDate
    );

    // Get all distinct symbols traded on a specific date
    @Query("SELECT DISTINCT t.symbol FROM TradeRecord t WHERE t.tradeDate = :tradeDate")
    List<String> findDistinctSymbolsByTradeDate(@Param("tradeDate") LocalDate tradeDate);

}
