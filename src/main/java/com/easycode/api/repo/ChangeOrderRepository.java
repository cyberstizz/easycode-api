package com.easycode.api.repo;

import com.easycode.api.domain.ChangeOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeOrderRepository extends JpaRepository<ChangeOrder, UUID> {

    List<ChangeOrder> findByRequestIdOrderByCreatedAtDesc(UUID requestId);
}
