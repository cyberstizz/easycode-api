package com.easycode.api.repo;

import com.easycode.api.domain.ChecklistTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

    Optional<ChecklistTemplate> findFirstByProjectTypeOrderByVersionDesc(String projectType);

    List<ChecklistTemplate> findAllByOrderByProjectTypeAscVersionDesc();
}