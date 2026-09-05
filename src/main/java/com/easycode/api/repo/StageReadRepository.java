package com.easycode.api.repo;

import com.easycode.api.domain.StageRead;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageReadRepository extends JpaRepository<StageRead, StageRead.Key> {
    List<StageRead> findByStageId(UUID stageId);
}