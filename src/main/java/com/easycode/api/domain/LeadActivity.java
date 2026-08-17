package com.easycode.api.domain;

import com.easycode.api.domain.enums.ActivityOutcome;
import com.easycode.api.domain.enums.ActivityType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
