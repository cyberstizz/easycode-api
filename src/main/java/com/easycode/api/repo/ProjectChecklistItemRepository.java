package com.easycode.api.repo;

import com.easycode.api.domain.ProjectChecklistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectChecklistItemRepository extends JpaRepository<ProjectChecklistItem, UUID> {
    List<ProjectChecklistItem> findByProjectIdOrderByStageKeyAscPositionAsc(UUID projectId);

    long countByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}