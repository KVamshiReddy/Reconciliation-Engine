package com.drivewealth.Reconciliation_Engine.kafka;

import com.drivewealth.Reconciliation_Engine.ledger.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeEventConsumer {

    private final ObjectMapper objectMapper;
    private final LedgerService ledgerService;

    @KafkaListener (
            topics = "trades_raw",
            groupId = "reconciliation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume (@Payload String message,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                         @Header(KafkaHeaders.OFFSET) long offset)
    {
        log.info("Received message from partition={} offset={}", partition, offset);;

        try{
            TradeEvent event = objectMapper.readValue(message, TradeEvent.class);
            log.info("Processing trade: fillId={} symbol={} shares={}",
                    event.getFillId(), event.getSymbol(), event.getShares());

            // Hand off to ledger service
            ledgerService.processTradeEvent(event);
        } catch (Exception e) {
            log.error("Failed to process message at partition={} offset={}: {}",
                    partition, offset, e.getMessage());
        }
    }
}
