package com.easycode.api.domain;

import com.easycode.api.domain.enums.ProjectStatus;
import com.easycode.api.domain.enums.StageKey;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private StageKey currentStage = StageKey.DISCOVERY;

    @Column(name = "contract_cents")
    private Integer contractCents;

    @Column(name = "deposit_cents")
    private Integer depositCents;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "est_launch_at")
    private Instant estLaunchAt;

    @Column(name = "live_url")
    private String liveUrl;

    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "plan_id")
    private UUID planId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
