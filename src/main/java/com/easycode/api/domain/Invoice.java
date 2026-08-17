package com.easycode.api.domain;

import com.easycode.api.domain.enums.InvoiceKind;
import com.easycode.api.domain.enums.InvoiceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, unique = true)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceKind kind = InvoiceKind.ONE_OFF;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "amount_paid_cents", nullable = false)
    private Integer amountPaidCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Only set for subscription invoices that originate in Stripe. */
    @Column(name = "stripe_invoice_id")
    private String stripeInvoiceId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "invoice_id")
    @OrderBy("position asc")
    private List<InvoiceLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public int balanceCents() {
        return Math.max(0, amountCents - amountPaidCents);
    }
}
