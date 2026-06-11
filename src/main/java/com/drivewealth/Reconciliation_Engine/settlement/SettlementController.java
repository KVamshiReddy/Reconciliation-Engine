package com.drivewealth.Reconciliation_Engine.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
@Slf4j
public class SettlementController {

    private final SettlementSimulator settlementSimulator;

    @PostMapping("/generate")
    public ResponseEntity<SettlementResult> generateSettlement(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        LocalDate settlementDate = date != null ? date : LocalDate.now();
        log.info("Settlement Requested for date:{}", settlementDate);
            SettlementResult result = settlementSimulator.getSettlementRecords(settlementDate);
            return ResponseEntity.ok(result);
        }

    @GetMapping("/{symbol}")
    public ResponseEntity<?> getSettlementRecord(
            @PathVariable String symbol,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate settlementDate = date != null ? date : LocalDate.now();
        return settlementSimulator.
                getSettlementRecord(symbol, settlementDate).
                map(ResponseEntity::ok).
                orElse(ResponseEntity.notFound().build());
    }
}
