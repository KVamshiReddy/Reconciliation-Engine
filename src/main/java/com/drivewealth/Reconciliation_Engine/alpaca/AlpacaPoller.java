package com.drivewealth.Reconciliation_Engine.alpaca;

import com.drivewealth.Reconciliation_Engine.kafka.TradeEvent;
import com.drivewealth.Reconciliation_Engine.kafka.TradeEventProducer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlpacaPoller {

    private final AlpacaOrderService alpacaOrderService;
    private final TradeEventProducer tradeEventProducer;
    private final AlpacaProperties properties;

    private final List<String> processedOrderIds = new ArrayList<>();

    @Scheduled(fixedDelay = 30000)
    public void pollForFills() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Polling Alpaca for filled orders");

        try {
            JsonNode filledOrders = alpacaOrderService.getWebClient()
                    .get()
                    .uri("/v2/orders?status=filled&limit=50")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (filledOrders == null || !filledOrders.isArray()
                    || filledOrders.size() == 0) {
                log.info("No filled orders found");
                return;
            }

            log.info("Found {} filled orders from Alpaca", filledOrders.size());

            for (JsonNode order : filledOrders) {
                String orderId = order.get("id").asText();

                if (processedOrderIds.contains(orderId)) {
                    continue;
                }

                JsonNode filledQtyNode = order.get("filled_qty");
                if (filledQtyNode == null ||
                        new BigDecimal(filledQtyNode.asText())
                                .compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                TradeEvent event = convertToTradeEvent(order);
                tradeEventProducer.publish(event);
                processedOrderIds.add(orderId);

                log.info("Published Alpaca fill to Kafka: orderId={} symbol={} qty={} price={}",
                        orderId,
                        order.get("symbol").asText(),
                        order.get("filled_qty").asText(),
                        order.get("filled_avg_price").asText());
            }

        } catch (Exception e) {
            log.error("Error polling Alpaca fills: {}", e.getMessage());
        }
    }

    private TradeEvent convertToTradeEvent(JsonNode order) {
        BigDecimal shares = new BigDecimal(order.get("filled_qty").asText());
        BigDecimal price = new BigDecimal(order.get("filled_avg_price").asText());
        BigDecimal notional = shares.multiply(price);

        return TradeEvent.builder()
                .fillId("alpaca_" + order.get("id").asText().substring(0, 8))
                .symbol(order.get("symbol").asText())
                .shares(shares)
                .price(price)
                .notional(notional)
                .userId("alpaca_paper")
                .tradeDate(LocalDate.now())
                .build();
    }
}