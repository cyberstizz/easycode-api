package com.easycode.api.web.dto;

import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.InvoiceLine;
import com.easycode.api.domain.Plan;
import com.easycode.api.domain.Subscription;
import com.easycode.api.domain.enums.InvoiceKind;
import com.easycode.api.domain.enums.InvoiceStatus;
import com.easycode.api.domain.enums.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {

    private BillingDtos() {}

    public record LineInput(@NotBlank String description, double quantity, int unitCents) {}

    public record InvoiceCreate(
            @NotNull UUID orgId,
            UUID projectId,
            InvoiceKind kind,
            String memo,
            Instant dueAt,
            @NotEmpty @Valid List<LineInput> lines) {}

    public record SubscriptionStart(
            UUID orgId,
            @NotNull UUID planId,
            String paymentMethodId,
            Short termMonths) {}

    public record LineView(String description, BigDecimal quantity, int unitCents, int totalCents) {
        public static LineView of(InvoiceLine l) {
            return new LineView(l.getDescription(), l.getQuantity(), l.getUnitCents(), l.totalCents());
        }
    }

    public record InvoiceView(
            UUID id,
            UUID orgId,
            UUID projectId,
            String number,
            InvoiceKind kind,
            InvoiceStatus status,
            int amountCents,
            int amountPaidCents,
            int balanceCents,
            String memo,
            Instant dueAt,
            Instant sentAt,
            Instant paidAt,
            Instant createdAt,
            List<LineView> lines) {

        public static InvoiceView of(Invoice i, boolean withLines) {
            return new InvoiceView(
                    i.getId(), i.getOrgId(), i.getProjectId(), i.getNumber(), i.getKind(), i.getStatus(),
                    i.getAmountCents(), i.getAmountPaidCents(), i.balanceCents(), i.getMemo(),
                    i.getDueAt(), i.getSentAt(), i.getPaidAt(), i.getCreatedAt(),
                    withLines ? i.getLines().stream().map(LineView::of).toList() : List.of());
        }
    }

    public record PlanView(
            UUID id, String name, int priceCents, String interval, BigDecimal includedHours,
            List<String> features, boolean purchasable) {

        public static PlanView of(Plan p) {
            return new PlanView(
                    p.getId(), p.getName(), p.getPriceCents(), p.getBillingInterval(), p.getIncludedHours(),
                    p.getFeatures(), p.getStripePriceId() != null && !p.getStripePriceId().isBlank());
        }
    }

    public record SubscriptionView(
            UUID id, UUID planId, String planName, SubscriptionStatus status,
            Short termMonths, Instant currentPeriodEnd, Instant cancelAt) {

        public static SubscriptionView of(Subscription s, String planName) {
            return new SubscriptionView(
                    s.getId(), s.getPlanId(), planName, s.getStatus(), s.getTermMonths(),
                    s.getCurrentPeriodEnd(), s.getCancelAt());
        }
    }

    public record IntentView(String clientSecret, String intentId, int amountCents) {}

    public record BillingSummary(
            long amountDueCents,
            List<InvoiceView> invoices,
            List<SubscriptionView> subscriptions,
            List<PlanView> plans) {}
}
