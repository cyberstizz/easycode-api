package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One checklist line on one project.
 *
 * Title and guidance are COPIED from the template rather than joined, so
 * editing a checklist document never rewrites the words on a project that is
 * already underway.
 */
@Entity
@Table(name = "project_checklist_items")
@Getter
@Setter
public class ProjectChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "stage_key", nullable = false)
    private String stageKey;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String guidance;

    @Column(nullable = false)
    private boolean emphasis;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_by")
    private UUID doneBy;

    @Column(name = "done_by_name")
    private String doneByName;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}