package com.drivewealth.Reconciliation_Engine.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Optional<ReconciliationRun> findByRunId(String runId);

    List<ReconciliationRun> findByRunDate(LocalDate runDate);

    List<ReconciliationRun> findByStatus(String status);

    boolean existsByRunDateAndStatus(LocalDate runDate, String status);

}
