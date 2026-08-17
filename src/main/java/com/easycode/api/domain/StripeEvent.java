package com.easycode.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Stripe retries. This makes the webhook idempotent. */
@Entity
@Table(name = "stripe_events")
@Getter
@Setter
public class StripeEvent {

    @Id
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();
}
