package com.easycode.api.repo;

import com.easycode.api.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findTop100ByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
