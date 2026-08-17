package com.easycode.api.service;

import com.easycode.api.domain.AuditLog;
import com.easycode.api.repo.AuditLogRepository;
import com.easycode.api.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Every state change worth arguing about later gets a timestamped row. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repo;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void record(AuthPrincipal actor, String action, String entityType, UUID entityId) {
        record(actor, action, entityType, entityId, Map.of());
    }

    public void record(
            AuthPrincipal actor, String action, String entityType, UUID entityId, Map<String, ?> diff) {
        try {
            AuditLog row = new AuditLog();
            row.setActorId(actor == null ? null : actor.userId());
            row.setActorEmail(actor == null ? "system" : actor.email());
            row.setAction(action);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setDiff(diff == null || diff.isEmpty() ? null : mapper.writeValueAsString(diff));
            repo.save(row);
        } catch (Exception e) {
            // auditing must never break the request it is auditing
            log.error("Failed to write audit row action={} entity={} id={}", action, entityType, entityId, e);
        }
    }
}
