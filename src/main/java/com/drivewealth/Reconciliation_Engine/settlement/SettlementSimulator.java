package com.drivewealth.Reconciliation_Engine.settlement;

import com.drivewealth.Reconciliation_Engine.ledger.TradeRecordRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementSimulator {

    private final TradeRecordRepository tradeRecordRepository;
    private final SettlementRecordRepository settlementRecordRepository;

    @Transactional
    @Retry(name = "settlementSimulator")
    public SettlementResult getSettlementRecords(LocalDate settlementDate) {
        log.info("Starting settlement generation for the date:{}", settlementDate);
        List<String> symbols = tradeRecordRepository.findDistinctSymbolsByTradeDate(settlementDate);
        if (symbols.isEmpty()) {
            log.warn("No trades found on the settlement date:{}", settlementDate);
            return SettlementResult.builder().
                    settlementDate(settlementDate).
                    message("No trades found on the given date").
                    symbolsProcessed(0).
                    symbolsSkipped(0).
                    build();
        }

        log.info("Found {} symbols to settle on the given date {}", symbols.size(), settlementDate);

        int processed = 0;
        int skipped = 0;

        for(String symbol : symbols) {
            if (settlementRecordRepository.existsBySymbolAndSettlementDate(symbol, settlementDate)) {
                log.warn("Settlement record already exists for the symbol;{}", symbol);
                skipped++;
                continue;
            }

            BigDecimal internalPosition = tradeRecordRepository.sumSharesBySymbolAndTradeDate(symbol, settlementDate);
            log.info("Symbol:{} has total shares {} - 8 decimal precision", symbol, internalPosition);
            BigDecimal settledShares = internalPosition.setScale(2, RoundingMode.HALF_UP);
            log.info("Symbol: {} | Settled shares (2dp):    {}", symbol, settledShares);
            log.info("Symbol: {} | Rounding difference:     {}",
                    symbol, internalPosition.subtract(settledShares));
            SettlementRecord record = SettlementRecord.builder().
                    settlementDate(settlementDate).
                    symbol(symbol).
                    settledShares(settledShares).
                    build();

            settlementRecordRepository.save(record);
            processed++;

        }

        log.info("Settlement generation complete. Processed: {} Skipped: {}",
                processed, skipped);

        return SettlementResult.builder()
                .settlementDate(settlementDate)
                .symbolsProcessed(processed)
                .symbolsSkipped(skipped)
                .message("Settlement generation complete")
                .build();
    }

    public Optional<SettlementRecord> getSettlementRecord(
            String symbol, LocalDate date) {
        return settlementRecordRepository
                .findBySymbolAndSettlementDate(symbol, date);
    }

}
