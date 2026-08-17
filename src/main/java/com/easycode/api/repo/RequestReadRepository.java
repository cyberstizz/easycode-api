package com.easycode.api.repo;

import com.easycode.api.domain.RequestRead;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestReadRepository extends JpaRepository<RequestRead, RequestRead.Key> {

    Optional<RequestRead> findByRequestIdAndUserId(UUID requestId, UUID userId);

    List<RequestRead> findByUserId(UUID userId);
}
