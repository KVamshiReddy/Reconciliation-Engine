package com.drivewealth.Reconciliation_Engine.alpaca;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alpaca")
@RequiredArgsConstructor
@Slf4j
public class AlpacaController {

    private final AlpacaOrderService alpacaOrderService;

    @PostMapping("/orders/place-all")
    public ResponseEntity<List<String>> placeOrdersForAllSymbols() {
        log.info("Placing Alpaca paper orders for all symbols");
        List<String> orderIds = alpacaOrderService.placeOrdersForAllSymbols();
        return ResponseEntity.ok(orderIds);
    }
}