package com.easycode.api.repo;

import com.easycode.api.domain.MaintenanceVisit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceVisitRepository extends JpaRepository<MaintenanceVisit, UUID> {

    /** The single open visit for a project, if there is one. */
    Optional<MaintenanceVisit> findFirstByProjectIdAndStatusOrderByDueOnAsc(UUID projectId, String status);

    /** Everything still open on or before a date — the "due and overdue" query. */
    List<MaintenanceVisit> findByStatusAndDueOnLessThanEqualOrderByDueOnAsc(String status, LocalDate through);

    /** Completed visits a client may see, newest first. */
    List<MaintenanceVisit> findByProjectIdAndStatusOrderByCompletedAtDesc(UUID projectId, String status);

    long countByStatusAndDueOnLessThan(String status, LocalDate before);
}
