package com.drivewealth.Reconciliation_Engine.breaks;

import com.drivewealth.Reconciliation_Engine.config.ReconciliationProperties;
import com.drivewealth.Reconciliation_Engine.constants.Constants;
import com.drivewealth.Reconciliation_Engine.ledger.TradeRecordRepository;
import com.drivewealth.Reconciliation_Engine.settlement.SettlementRecord;
import com.drivewealth.Reconciliation_Engine.settlement.SettlementRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BreakDetectionService {

    private final TradeRecordRepository tradeRecordRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final BreakRepository breakRepository;
    private final ReconciliationProperties reconciliationProperties;
    private final ExecutorService executorService;


    public BreakDetectionService(TradeRecordRepository tradeRecordRepository,
                                 SettlementRecordRepository settlementRecordRepository,
                                 BreakRepository breakRepository,
                                 ReconciliationProperties reconciliationProperties,
                                 @Qualifier("reconciliationExecutor") ExecutorService executorService) {
        this.executorService = executorService;
        this.reconciliationProperties = reconciliationProperties;
        this.settlementRecordRepository = settlementRecordRepository;
        this.breakRepository = breakRepository;
        this.tradeRecordRepository = tradeRecordRepository;
    }


    public BreakDetectionResult detectBreaks(String runId, LocalDate tradeDate) {
        log.info("Detecting Breaks for runId {} and trade date {}", runId, tradeDate);

        long startTime = System.currentTimeMillis();


        List<String> ledgerSymbols = tradeRecordRepository.findDistinctSymbolsByTradeDate(tradeDate);

        List<SettlementRecord> settlements = settlementRecordRepository.findBySettlementDate(tradeDate);
        List<String> settlementSymbols = settlements.stream().map(SettlementRecord :: getSymbol).toList();

        List<CompletableFuture<List<Break>>> futures = new ArrayList<>();

        for (String symbol : ledgerSymbols) {
            if (!settlementSymbols.contains(symbol)) {
            CompletableFuture<List<Break>> future = CompletableFuture
                    .supplyAsync(() -> detectMissingSettlement(symbol, runId, tradeDate), executorService)
                            .exceptionally(ex -> {
                                log.error("Error processing missing settlement for {} : {}",
                                        symbol,
                                        ex.getMessage());
                                return List.of();
                            });
            futures.add(future);
            }
        }

        for (String symbol : settlementSymbols) {
            if (!ledgerSymbols.contains(symbol)) {
                CompletableFuture<List<Break>> future = CompletableFuture
                        .supplyAsync(() -> detectMissingInternal(symbol, runId, tradeDate, settlements)
                        , executorService)
                                .exceptionally(ex -> {
                                    log.error("Error processing missing internal for {} : {}",
                                            symbol,
                                            ex.getMessage());
                                    return List.of();
                                });
                futures.add(future);
            }
        }

        for (SettlementRecord record : settlements) {
            String symbol = record.getSymbol();
            if (ledgerSymbols.contains(symbol)) {
                CompletableFuture<List<Break>> future = CompletableFuture
                        .supplyAsync(() -> comparePositions(symbol, runId, tradeDate, record),
                                executorService)
                        .exceptionally(ex -> {
                            log.error("Error tallying positions for {} : {}", symbol, ex.getMessage());
                            return List.of();
                        });
                futures.add(future);
            }
        }

        List<Break> allBreaks = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        breakRepository.saveAll(allBreaks);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Parallel break detection complete in {}ms. " +
                        "Total: {} Rounding: {} Critical: {} Missing: {}",
                duration,
                allBreaks.size(),
                allBreaks.stream().filter(b -> b.getCategory() == Constants.BreakCategory.ROUNDING).count(),
                allBreaks.stream().filter(b -> b.getCategory() == Constants.BreakCategory.CRITICAL).count(),
                allBreaks.stream().filter(b ->
                        b.getCategory() == Constants.BreakCategory.MISSING_SETTLEMENT ||
                                b.getCategory() == Constants.BreakCategory.MISSING_INTERNAL).count()
        );

        return BreakDetectionResult.builder()
                .runId(runId)
                .tradeDate(tradeDate)
                .totalBreaks(allBreaks.size())
                .roundingBreaks((int) allBreaks.stream()
                        .filter(b -> b.getCategory() == Constants.BreakCategory.ROUNDING).count())
                .criticalBreaks((int) allBreaks.stream()
                        .filter(b -> b.getCategory() == Constants.BreakCategory.CRITICAL).count())
                .missingBreaks((int) allBreaks.stream()
                        .filter(b -> b.getCategory() == Constants.BreakCategory.MISSING_SETTLEMENT ||
                                b.getCategory() == Constants.BreakCategory.MISSING_INTERNAL).count())
                .durationMs(duration)
                .build();
    }

    private List<Break> detectMissingSettlement(
            String symbol, String runId, LocalDate tradeDate) {
        log.warn("No settlement record found for Symbol {}", symbol);
        BigDecimal internalPosition = tradeRecordRepository
                .sumSharesBySymbolAndTradeDate(symbol, tradeDate);

        Break missingBreak = Break.builder()
                .symbol(symbol)
                .category(Constants.BreakCategory.MISSING_SETTLEMENT)
                .status(Constants.BreakStatus.ESCALATED)
                .breakId("BRK-" + UUID.randomUUID().toString().substring(0,8))
                .runId(runId)
                .runId(runId)
                .settlementPosition(BigDecimal.ZERO)
                .difference(internalPosition)
                .detectedAt(LocalDate.now())
                .resolutionNote("Symbol is present in the ledger but cannot be found in the settlement")
                .internalPosition(internalPosition)
                .build();

        return List.of(missingBreak);

    }

    private List<Break> detectMissingInternal(
            String symbol,
            String runId,
            LocalDate tradeDate,
            List<SettlementRecord> settlementRecords) {
        log.warn("No Ledger Entry found for Symbol {}", symbol);
        BigDecimal settledShares = settlementRecords.stream()
                .filter(s -> s.getSymbol().equalsIgnoreCase(symbol))
                .findFirst()
                .map(SettlementRecord::getSettledShares)
                .orElse(BigDecimal.ZERO);
        Break missingBreak = Break.builder()
                .symbol(symbol)
                .category(Constants.BreakCategory.MISSING_INTERNAL)
                .status(Constants.BreakStatus.ESCALATED)
                .breakId("BRK-" + UUID.randomUUID().toString().substring(0,8))
                .runId(runId)
                .settlementPosition(settledShares)
                .difference(settledShares.negate())
                .detectedAt(LocalDate.now())
                .resolutionNote("Symbol is present in the settlement record but cannot be found in the ledger")
                .internalPosition(BigDecimal.ZERO)
                .build();
        return List.of(missingBreak);
    }

    private List<Break> comparePositions(
            String symbol,
            String runId,
            LocalDate tradeDate,
            SettlementRecord settlementRecord) {
        BigDecimal internalPosition = tradeRecordRepository.
                sumSharesBySymbolAndTradeDate(symbol, tradeDate);

        BigDecimal settledShares = settlementRecord.getSettledShares();

        BigDecimal difference = internalPosition
                .subtract(settledShares.setScale(8, RoundingMode.HALF_UP));

        BigDecimal absDifference = difference.abs();

        if (absDifference.compareTo(BigDecimal.ZERO) == 0) {
            log.info("NO BREAKS DETECTED - Internal Ledger and Settlement Records match perfectly for Symbol {}", symbol);
            return List.of();
        }

        Constants.BreakCategory category;
        Constants.BreakStatus status;
        String note;

        if (absDifference.compareTo(reconciliationProperties.getRoundingThreshold()) <= 0 ) {
            category = Constants.BreakCategory.ROUNDING;
            status = Constants.BreakStatus.DETECTED;
            note = String.format(
                    "BREAK DETECTED - Rounding Difference of %s exceeds the threshold value %s",
                    absDifference,
                    reconciliationProperties.getRoundingThreshold());
        } else {
            category = Constants.BreakCategory.CRITICAL;
            status = Constants.BreakStatus.ESCALATED;
            note = String.format(
                    "BREAK DETECTED - Critical Difference of %s exceeds the threshold value %s",
                    absDifference,
                    reconciliationProperties.getCriticalBreakThreshold());
        }

        log.info("Break detected — symbol={} internal={} settled={} diff={} category={}",
                symbol, internalPosition, settledShares, difference, category);

        Break detectedBreak = Break.builder()
                .symbol(symbol)
                .category(category)
                .status(status)
                .breakId("BRK-" + UUID.randomUUID().toString().substring(0,8))
                .runId(runId)
                .settlementPosition(settledShares)
                .difference(difference)
                .detectedAt(LocalDate.now())
                .resolutionNote(note)
                .internalPosition(internalPosition)
                .build();
        return List.of(detectedBreak);
    }





}

