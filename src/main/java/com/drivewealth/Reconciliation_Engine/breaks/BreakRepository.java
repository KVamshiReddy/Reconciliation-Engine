package com.drivewealth.Reconciliation_Engine.breaks;

import com.drivewealth.Reconciliation_Engine.constants.Constants;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BreakRepository extends JpaRepository<Break, Long> {

    // Get all breaks for a specific reconciliation run
    List<Break> findByRunId(String runId);

    // Get all breaks for a specific symbol
    List<Break> findBySymbol(String symbol);

    // Get all breaks with a specific status
    List<Break> findByStatus(Constants.BreakStatus status);

    // Get all breaks with a specific category
    List<Break> findByCategory(Constants.BreakCategory category);

    // Get a specific break by its breakId
    Optional<Break> findByBreakId(String breakId);

    // Get all unresolved breaks
    List<Break> findByStatusIn(List<Constants.BreakStatus> statuses);

    // Count breaks by category for a specific run
    long countByRunIdAndCategory(String runId, Constants.BreakCategory category);

    // Count breaks by status for a specific run
    long countByRunIdAndStatus(String runId, Constants.BreakStatus status);

}
