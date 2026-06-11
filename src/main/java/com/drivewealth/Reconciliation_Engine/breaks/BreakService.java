package com.drivewealth.Reconciliation_Engine.breaks;

import com.drivewealth.Reconciliation_Engine.audit.AuditLog;
import com.drivewealth.Reconciliation_Engine.audit.AuditLogRepository;
import com.drivewealth.Reconciliation_Engine.config.ReconciliationProperties;
import com.drivewealth.Reconciliation_Engine.constants.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BreakService {

    private final ReconciliationProperties reconciliationProperties;
    private final BreakRepository breakRepository;
    private final AuditLogRepository auditLogRepository;


    @Transactional
    public AutoResolutionResult startAutoResolving(String runId) {
        log.info("Starting to AUTO-RESOLVE ROUNDING BREAKS for runId : {}", runId);
        List<Break> roundingBreaks = breakRepository
                .findByRunId(runId)
                .stream()
                .filter(a -> a.getCategory() == Constants.BreakCategory.ROUNDING
                        && a.getStatus() == Constants.BreakStatus.DETECTED)
                .toList();

        int resolved = 0;

        for (Break b : roundingBreaks) {
            if (b.getDifference().abs().compareTo(reconciliationProperties.getRoundingThreshold()) <= 0) {
                b.setStatus(Constants.BreakStatus.AUTO_RESOLVED);
                b.setResolvedAt(LocalDate.now());
                b.setResolutionNote(
                        String.format("AUTO-RESOLVED : Rounding Difference %s is Within the Estimated Threshold %s ",
                                b.getDifference().abs(),
                                reconciliationProperties.getRoundingThreshold())
                );
                breakRepository.save(b);
                AuditLog resolutionLog = AuditLog.builder()
                        .action("AUTO_RESOLVED")
                        .entityType("ROUNDING_BREAK")
                        .entityId(b.getBreakId())
                        .detail(String.format(
                                "Symbol %s rounding break resolved. Difference: %s",
                                b.getSymbol(), b.getDifference()))
                        .build();

                auditLogRepository.save(resolutionLog);

                resolved++;
                log.info("Auto resolved break: symbol={} difference={}",
                        b.getSymbol(), b.getDifference());
            }
        }
        log.info("Auto resolution complete. Resolved {} of {} rounding breaks",
                resolved, roundingBreaks.size());

        return AutoResolutionResult.builder()
                .runId(runId)
                .totalRoundingBreaks(roundingBreaks.size())
                .autoResolved(resolved)
                .build();
    }

    @Transactional
    public Optional<Break> closeBreakManual(String breakId, String resolutionNote) {
        Optional<Break> breakOptional = breakRepository.findByBreakId(breakId);

        if (breakOptional.isEmpty()) {
            log.warn("Break Not Found with the given ID : {}", breakId);
            return Optional.empty();
        }

        Break b = breakOptional.get();

        if (b.getStatus() == Constants.BreakStatus.CLOSED) {
            log.warn("Break With ID : {} Has Already Been Closed", breakId);
            return Optional.of(b);
        }

        b.setStatus(Constants.BreakStatus.CLOSED);
        b.setResolutionNote(resolutionNote);
        b.setResolvedAt(LocalDate.now());

        breakRepository.save(b);

        auditLogRepository.save(AuditLog.builder()
                .entityType("BREAK")
                .entityId(breakId)
                .action("MANUALLY_CLOSED")
                .detail(String.format("Break closed by operations. Note: %s",
                        resolutionNote))
                .build());

        log.info("Break manually closed: breakId={} note={}", breakId, resolutionNote);

        return Optional.of(b);

    }

    public List<Break> getOpenBreaks() {
        return breakRepository.findByStatusIn(
                List.of(Constants.BreakStatus.DETECTED,
                        Constants.BreakStatus.ESCALATED,
                        Constants.BreakStatus.UNDER_REVIEW));
    }

    public List<AuditLog> getBreakAuditTrail(String breakId) {
        return auditLogRepository
                .findByEntityTypeAndEntityId("BREAK", breakId);
    }

}
