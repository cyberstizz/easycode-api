package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** The maintenance rhythm for one project. See V5__maintenance.sql. */
@Entity
@Table(name = "maintenance_schedules")
@Getter
@Setter
public class MaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "cadence_days", nullable = false)
    private short cadenceDays = 14;

    /** The day the rhythm starts from — normally the day the site went live. */
    @Column(name = "anchor_on", nullable = false)
    private LocalDate anchorOn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
