package com.easycode.api.repo;

import com.easycode.api.domain.ChecklistTemplateItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateItemRepository extends JpaRepository<ChecklistTemplateItem, UUID> {
    List<ChecklistTemplateItem> findByTemplateIdOrderByStageKeyAscPositionAsc(UUID templateId);
}