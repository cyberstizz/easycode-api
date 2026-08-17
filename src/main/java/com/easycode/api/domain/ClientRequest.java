package com.easycode.api.domain;

import com.easycode.api.domain.enums.BillingDisposition;
import com.easycode.api.domain.enums.Priority;
import com.easycode.api.domain.enums.RequestStatus;
import com.easycode.api.domain.enums.RequestType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Decision 3: ONE object covers update requests, questions, new project asks and bugs.
 * The conversation lives inside it, attached to a project, permanently.
 */
@Entity
@Table(name = "requests")
@Getter
@Setter
public class ClientRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType type = RequestType.UPDATE;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.NEW;

    /** The scope-creep guard. Set by staff, drives change orders. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingDisposition billing = BillingDisposition.UNSET;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
