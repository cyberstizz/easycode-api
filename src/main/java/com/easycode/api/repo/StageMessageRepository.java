package com.easycode.api.repo;

import com.easycode.api.domain.StageMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageMessageRepository extends JpaRepository<StageMessage, UUID> {
    List<StageMessage> findByStageIdOrderByCreatedAtAsc(UUID stageId);

    long countByStageId(UUID stageId);
}