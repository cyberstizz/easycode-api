package com.easycode.api.repo;

import com.easycode.api.domain.RequestMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestMessageRepository extends JpaRepository<RequestMessage, UUID> {

    List<RequestMessage> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    List<RequestMessage> findByRequestIdAndInternalOnlyFalseOrderByCreatedAtAsc(UUID requestId);

    long countByRequestIdAndCreatedAtAfter(UUID requestId, Instant after);

    long countByRequestId(UUID requestId);
}
