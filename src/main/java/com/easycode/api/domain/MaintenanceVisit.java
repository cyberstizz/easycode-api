package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One scheduled maintenance visit.
 *
 * {@code clientReport} is the only field a client ever sees, and only after the
 * visit is DONE. {@code dueOn} is never exposed to the portal.
 */
@Entity
@Table(name = "maintenance_visits")
@Getter
@Setter
public class MaintenanceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false)
    private String status = "DUE";

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_by_name")
    private String completedByName;

    @Column(name = "client_report", columnDefinition = "text")
    private String clientReport;

    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    /** How many whole cadence periods were skipped to reach the next due date. */
    @Column(name = "missed_cycles", nullable = false)
    private short missedCycles;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
