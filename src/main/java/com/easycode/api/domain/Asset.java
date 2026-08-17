package com.easycode.api.domain;

import com.easycode.api.domain.enums.AssetVisibility;
import com.easycode.api.domain.enums.UploadState;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "assets")
@Getter
@Setter
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "stage_id")
    private UUID stageId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    /** Object key in the private R2 bucket. Never exposed to the browser. */
    @Column(name = "r2_key", nullable = false, unique = true)
    private String r2Key;

    @Column(nullable = false)
    private String filename;

    private String mime;
    private Long bytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetVisibility visibility = AssetVisibility.CLIENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_state", nullable = false)
    private UploadState uploadState = UploadState.PENDING;

    private String caption;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
