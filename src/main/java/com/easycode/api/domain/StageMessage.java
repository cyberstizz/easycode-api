package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One message in the thread under a stage update. See V3__stage_messages.sql. */
@Entity
@Table(name = "stage_messages")
@Getter
@Setter
public class StageMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "author_id")
    private UUID authorId;

    /** Snapshotted at write time so the thread still reads correctly if the user is deleted. */
    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_role", nullable = false)
    private String authorRole;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}