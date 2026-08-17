package com.easycode.api.domain;

import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.domain.enums.StageStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "project_stages")
@Getter
@Setter
public class ProjectStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_key", nullable = false)
    private StageKey stageKey;

    @Column(name = "position", nullable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageStatus status = StageStatus.PENDING;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Shown to the client on the tracker. */
    @Column(name = "client_note", columnDefinition = "text")
    private String clientNote;

    /** Never leaves the admin console. */
    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
