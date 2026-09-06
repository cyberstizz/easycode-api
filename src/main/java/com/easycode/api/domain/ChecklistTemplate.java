package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One version of one project type's checklist. See V4__checklists.sql. */
@Entity
@Table(name = "checklist_templates")
@Getter
@Setter
public class ChecklistTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_type", nullable = false)
    private String projectType;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int version;

    /** Hash of the seed items. A change here is what triggers a new version. */
    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}