package com.drivewealth.Reconciliation_Engine.ledger;

import com.drivewealth.Reconciliation_Engine.kafka.TradeEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final TradeRecordRepository tradeRecordRepository;

    @Transactional
    @Retry(name = "ledgerService")
    @CircuitBreaker(name = "ledgerService", fallbackMethod = "processTradeEventFallback")
    public void processTradeEvent(TradeEvent event) {

        // Step 1 — Idempotency check
        // If this fillId already exists for this date, skip it
        boolean alreadyExists = tradeRecordRepository
                .existsByFillIdAndTradeDate(event.getFillId(), event.getTradeDate());

        if (alreadyExists) {
            log.warn("Duplicate trade detected — skipping: fillId={} date={}",
                    event.getFillId(), event.getTradeDate());
            return;
        }

        // Step 2 — Convert TradeEvent to TradeRecord
        TradeRecord record = TradeRecord.builder()
                .fillId(event.getFillId())
                .userId(event.getUserId())
                .symbol(event.getSymbol())
                .shares(event.getShares())
                .price(event.getPrice())
                .notional(event.getNotional())
                .tradeDate(event.getTradeDate())
                .build();

        // Step 3 — Save to PostgreSQL
        tradeRecordRepository.save(record);

        log.info("Trade saved to ledger: fillId={} symbol={} shares={} userId={}",
                record.getFillId(), record.getSymbol(),
                record.getShares(), record.getUserId());
    }

    public void processTradeEventFallback(TradeEvent event, Exception ex) {
        log.error("Circuit breaker triggered for ledgerService. " +
                        "Failed to process trade: fillId={} symbol={} error={}",
                event.getFillId(), event.getSymbol(), ex.getMessage());
    }

}