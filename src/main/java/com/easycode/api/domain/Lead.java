package com.easycode.api.domain;

import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.LeadStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Set once the lead converts. */
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "contact_name")
    private String contactName;

    private String email;
    private String phone;
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    /** Why you're calling them back. Without it, nextActionAt is a date with no context. */
    @Column(name = "next_action_note", columnDefinition = "text")
    private String nextActionNote;

    @Column(name = "est_value_cents")
    private Integer estValueCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "offered_tier")
    private DealTier offeredTier;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}