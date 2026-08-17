package com.easycode.api.repo;

import com.easycode.api.domain.LeadActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    List<LeadActivity> findByLeadIdOrderByOccurredAtDesc(UUID leadId);
}
