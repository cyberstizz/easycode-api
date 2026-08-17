package com.easycode.api.repo;

import com.easycode.api.domain.Lead;
import com.easycode.api.domain.enums.LeadStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    List<Lead> findByStatusOrderByUpdatedAtDesc(LeadStatus status);

    List<Lead> findByOwnerIdAndNextActionAtBeforeOrderByNextActionAtAsc(UUID ownerId, Instant before);

    List<Lead> findByNextActionAtBeforeOrderByNextActionAtAsc(Instant before);

    long countByStatus(LeadStatus status);
}
