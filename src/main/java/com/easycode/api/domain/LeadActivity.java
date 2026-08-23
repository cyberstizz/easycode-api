package com.easycode.api.domain;

import com.easycode.api.domain.enums.ActivityOutcome;
import com.easycode.api.domain.enums.ActivityType;
import com.easycode.api.domain.enums.DealTier;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One interaction with a lead.
 *
 * <p>The three fields added in V2 — durationSeconds, objectionTags, rungOffered —
 * are what let the pipeline answer "which objection kills deals" and "which offer
 * actually closes". Free text in {@code body} can be read; only these can be counted.
 */
@Entity
@Table(name = "lead_activities")
@Getter
@Setter
public class LeadActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type = ActivityType.CALL;

    @Enumerated(EnumType.STRING)
    private ActivityOutcome outcome;

    @Column(columnDefinition = "text")
    private String body;

    /** Connect time. Null for voicemail, no-answer, and non-call activities. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Structured push-back from this call, e.g. {"Trust","Cash flow"}.
     *
     * <p>A Postgres text[] rather than a join table: the vocabulary is short and
     * fixed, and it's only ever read in aggregate. JdbcTypeCode(ARRAY) is the
     * plain-Hibernate 6 mapping — no extra dependency required.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "objection_tags", columnDefinition = "text[]")
    private String[] objectionTags = new String[0];

    /**
     * Which rung went on the table during THIS call.
     *
     * <p>Distinct from {@code Lead.offeredTier}, which only holds the latest.
     * This is the history, and it's what shows whether you're dropping to the
     * floor too early.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rung_offered")
    private DealTier rungOffered;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}