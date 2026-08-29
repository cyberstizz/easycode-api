package com.easycode.api.repo;

import com.easycode.api.domain.Project;
import com.easycode.api.domain.enums.ProjectStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<Project> findByStatusOrderByUpdatedAtDesc(ProjectStatus status);

    List<Project> findTop50ByOrderByUpdatedAtDesc();

    long countByStatus(ProjectStatus status);

    long countByOrgId(UUID orgId);
}