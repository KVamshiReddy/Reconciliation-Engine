package com.drivewealth.Reconciliation_Engine.kafka;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeEventProducer {

    private static final String TOPIC = "trades_raw";

    private static final List<String> SYMBOLS = List.of(
            "TSLA", "AAPL", "NVDA", "MSFT", "AMZN",
            "GOOGL", "META", "NFLX", "AMD", "INTC"
    );

    private static final List<BigDecimal> STOCK_PRICES = List.of(
            new BigDecimal("248.73"),  // TSLA
            new BigDecimal("189.45"),  // AAPL
            new BigDecimal("875.20"),  // NVDA
            new BigDecimal("415.60"),  // MSFT
            new BigDecimal("192.30"),  // AMZN
            new BigDecimal("178.90"),  // GOOGL
            new BigDecimal("512.40"),  // META
            new BigDecimal("645.30"),  // NFLX
            new BigDecimal("168.50"),  // AMD
            new BigDecimal("42.30")    // INTC
    );

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TradeEvent generateAndPublish() {
        int index = ThreadLocalRandom.current().nextInt(SYMBOLS.size());
        String symbol = SYMBOLS.get(index);
        BigDecimal price = STOCK_PRICES.get(index);
        double randomNotional = ThreadLocalRandom.current().nextDouble(1.0, 500.0);
        BigDecimal notional = new BigDecimal(randomNotional).setScale(8, RoundingMode.HALF_UP);
        BigDecimal shares = notional
                .divide(price, 8, RoundingMode.HALF_UP);

        TradeEvent event = TradeEvent.builder().fillId("fill_" + UUID.randomUUID().toString().substring(0, 8))
                .userId("user_" + ThreadLocalRandom.current().nextInt(1000, 9999))
                .symbol(symbol)
                .shares(shares)
                .price(price)
                .notional(notional)
                .tradeDate(LocalDate.now())
                .build();
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getSymbol(), message);
            log.info("Published trade event: fillId={} symbol={} shares={} notional={}",
                    event.getFillId(), event.getSymbol(),
                    event.getShares(), event.getNotional());
        } catch (Exception e) {
            log.error("Failed to publish trade event", e);
            throw new RuntimeException("Failed to publish trade event", e);
        }

        return event;
    }

    public void publish(TradeEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getSymbol(), message);
            log.info("Published trade event: fillId={} symbol={} shares={} notional={}",
                    event.getFillId(), event.getSymbol(),
                    event.getShares(), event.getNotional());
        } catch (JsonProcessingException e) {
            log.error("Failed to publish trade event: fillId={}", event.getFillId(), e);
            throw new RuntimeException("Failed to publish trade event", e);
        }
    }

}

