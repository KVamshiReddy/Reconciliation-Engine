package com.drivewealth.Reconciliation_Engine.alpaca;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AlpacaOrderService {

    private final AlpacaProperties properties;
    private WebClient webClient;

    private static final List<String> SYMBOLS = List.of(
            "AAPL", "TSLA", "NVDA", "AMD", "GOOGL",
            "MSFT", "META", "AMZN", "NFLX", "INTC"
    );

    private static final String NOTIONAL_AMOUNT = "50";

    public AlpacaOrderService(AlpacaProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Alpaca integration disabled — using simulated trades");
            return;
        }

        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("APCA-API-KEY-ID", properties.getApiKey())
                .defaultHeader("APCA-API-SECRET-KEY", properties.getApiSecret())
                .defaultHeader("Content-Type", "application/json")
                .build();

        log.info("Alpaca WebClient initialized — paper trading mode");
        verifyConnection();
    }

    private void verifyConnection() {
        try {
            JsonNode account = webClient.get()
                    .uri("/v2/account")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            log.info("Alpaca account connected: buyingPower={}",
                    account.get("buying_power").asText());
        } catch (Exception e) {
            log.error("Failed to connect to Alpaca API: {}", e.getMessage());
        }
    }

    public String placeOrder(String symbol) {
        if (webClient == null) {
            throw new IllegalStateException("Alpaca WebClient not initialized");
        }

        try {
            String body = String.format(
                    "{\"symbol\":\"%s\",\"notional\":\"%s\",\"side\":\"buy\",\"type\":\"market\",\"time_in_force\":\"day\"}",
                    symbol, NOTIONAL_AMOUNT
            );

            JsonNode order = webClient.post()
                    .uri("/v2/orders")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String orderId = order.get("id").asText();
            log.info("Alpaca order placed: orderId={} symbol={} notional=${}",
                    orderId, symbol, NOTIONAL_AMOUNT);

            return orderId;

        } catch (Exception e) {
            log.error("Failed to place Alpaca order for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Alpaca order failed for " + symbol, e);
        }
    }

    public List<String> placeOrdersForAllSymbols() {
        log.info("Placing Alpaca paper orders for {} symbols", SYMBOLS.size());

        List<String> orderIds = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            try {
                orderIds.add(placeOrder(symbol));
            } catch (Exception e) {
                log.warn("Skipping {} due to error: {}", symbol, e.getMessage());
            }
        }
        return orderIds;
    }

    public AlpacaFillDetails getFillDetails(String orderId) {
        try {
            JsonNode order = webClient.get()
                    .uri("/v2/orders/" + orderId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode filledQtyNode = order.get("filled_qty");
            if (filledQtyNode == null || filledQtyNode.asText().equals("0")) {
                return null;
            }

            return AlpacaFillDetails.builder()
                    .orderId(orderId)
                    .symbol(order.get("symbol").asText())
                    .filledQty(new BigDecimal(order.get("filled_qty").asText()))
                    .filledAvgPrice(new BigDecimal(order.get("filled_avg_price").asText()))
                    .filledAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get fill details for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    public WebClient getWebClient() {
        return webClient;
    }

    public List<String> getSymbols() {
        return SYMBOLS;
    }
}