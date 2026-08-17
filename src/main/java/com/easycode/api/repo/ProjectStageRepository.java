package com.easycode.api.repo;

import com.easycode.api.domain.ProjectStage;
import com.easycode.api.domain.enums.StageKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectStageRepository extends JpaRepository<ProjectStage, UUID> {

    List<ProjectStage> findByProjectIdOrderByPositionAsc(UUID projectId);

    Optional<ProjectStage> findByProjectIdAndStageKey(UUID projectId, StageKey stageKey);
}
