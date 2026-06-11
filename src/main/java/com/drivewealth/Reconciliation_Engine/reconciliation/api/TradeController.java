package com.drivewealth.Reconciliation_Engine.reconciliation.api;

import com.drivewealth.Reconciliation_Engine.kafka.TradeEvent;
import com.drivewealth.Reconciliation_Engine.kafka.TradeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@Slf4j
public class TradeController {

    private final TradeEventProducer tradeEventProducer;

    @PostMapping("/simulate")
    public ResponseEntity<TradeEvent> simulateTrade() {
        log.info("Simulating a trade event");
        TradeEvent event = tradeEventProducer.generateAndPublish();
        return ResponseEntity.ok(event);
    }

    @PostMapping("/simulate/batch")
    public ResponseEntity<String> simulateBatch(@RequestParam(defaultValue = "10") int count) {
        log.info("Simulating {} trade events", count);
        for (int i = 0; i < count; i++) {
            tradeEventProducer.generateAndPublish();
        }
        return ResponseEntity.ok("Published " + count + " trade events to Kafka");
    }
}
