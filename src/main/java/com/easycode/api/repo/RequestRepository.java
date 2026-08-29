package com.easycode.api.repo;

import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.enums.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RequestRepository
        extends JpaRepository<ClientRequest, UUID>, JpaSpecificationExecutor<ClientRequest> {

    List<ClientRequest> findByOrgIdOrderByUpdatedAtDesc(UUID orgId);

    long countByStatusIn(List<RequestStatus> statuses);

    long countByOrgIdAndStatusIn(UUID orgId, List<RequestStatus> statuses);

    List<ClientRequest> findByStatusInOrderByCreatedAtAsc(List<RequestStatus> statuses);

    long countByDueAtBeforeAndStatusIn(Instant cutoff, List<RequestStatus> statuses);

    long countByOrgId(UUID orgId);
}