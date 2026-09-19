package com.easycode.api.repo;

import com.easycode.api.domain.MaintenanceSchedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, UUID> {
    Optional<MaintenanceSchedule> findByProjectId(UUID projectId);

    List<MaintenanceSchedule> findByOrgId(UUID orgId);

    List<MaintenanceSchedule> findByActiveTrue();
}
