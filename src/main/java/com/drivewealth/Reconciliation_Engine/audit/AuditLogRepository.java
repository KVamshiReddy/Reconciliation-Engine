package com.drivewealth.Reconciliation_Engine.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityId(
            String entityType, String entityId
    );

    List<AuditLog> findByEntityType(String entityType);

}
