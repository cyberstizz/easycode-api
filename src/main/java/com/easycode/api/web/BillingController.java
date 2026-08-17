package com.easycode.api.web;

import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.Plan;
import com.easycode.api.repo.PlanRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.BillingService;
import com.easycode.api.web.dto.BillingDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class BillingController {

    private final BillingService billing;
    private final AccessService access;
    private final PlanRepository plans;

    public BillingController(BillingService billing, AccessService access, PlanRepository plans) {
        this.billing = billing;
        this.access = access;
        this.plans = plans;
    }

    @GetMapping("/billing/summary")
    public BillingDtos.BillingSummary summary(
            @AuthenticationPrincipal AuthPrincipal me, @RequestParam(required = false) UUID orgId) {

        UUID scoped = access.resolveOrgId(me, orgId);
        access.requireOrg(me, scoped);
        return new BillingDtos.BillingSummary(
                billing.outstandingCents(scoped),
                billing.forOrg(scoped).stream().map(i -> BillingDtos.InvoiceView.of(i, false)).toList(),
                billing.subscriptionsFor(scoped).stream()
                        .map(s -> BillingDtos.SubscriptionView.of(s, planName(s.getPlanId())))
                        .toList(),
                billing.activePlans().stream().map(BillingDtos.PlanView::of).toList());
    }

    @GetMapping("/plans")
    public List<BillingDtos.PlanView> plans() {
        return billing.activePlans().stream().map(BillingDtos.PlanView::of).toList();
    }

    @GetMapping("/invoices/{id}")
    public BillingDtos.InvoiceView invoice(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        return BillingDtos.InvoiceView.of(access.invoice(me, id), true);
    }

    /**
     * Hands the in-app Payment Element its client_secret.
     * The invoice is not marked paid here — only the webhook does that.
     */
    @PostMapping("/invoices/{id}/payment-intent")
    public BillingDtos.IntentView payInvoice(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        Invoice invoice = access.invoice(me, id);
        BillingService.IntentResponse intent = billing.payInvoice(me, invoice);
        return new BillingDtos.IntentView(
                intent.clientSecret(), intent.intentId(), intent.amountCents());
    }

    /** Collect a card in-app for the maintenance plan without charging it yet. */
    @PostMapping("/billing/setup-intent")
    public BillingDtos.IntentView setupIntent(
            @AuthenticationPrincipal AuthPrincipal me, @RequestParam(required = false) UUID orgId) {
        UUID scoped = access.resolveOrgId(me, orgId);
        access.requireOrg(me, scoped);
        BillingService.IntentResponse intent = billing.setupIntent(me, scoped);
        return new BillingDtos.IntentView(intent.clientSecret(), intent.intentId(), 0);
    }

    @PostMapping("/subscriptions")
    public BillingDtos.SubscriptionView subscribe(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody BillingDtos.SubscriptionStart body) {

        UUID scoped = access.resolveOrgId(me, body.orgId());
        access.requireOrg(me, scoped);
        var sub = billing.startSubscription(
                me, scoped, body.planId(), body.paymentMethodId(), body.termMonths());
        return BillingDtos.SubscriptionView.of(sub, planName(sub.getPlanId()));
    }

    // ------------------------------------------------------------------ admin

    @GetMapping("/admin/invoices")
    public List<BillingDtos.InvoiceView> adminInvoices(
            @AuthenticationPrincipal AuthPrincipal me, @RequestParam(required = false) UUID orgId) {
        access.requireStaff(me);
        List<com.easycode.api.domain.Invoice> found =
                orgId != null ? billing.forOrg(orgId) : billing.allRecent();
        return found.stream().map(i -> BillingDtos.InvoiceView.of(i, false)).toList();
    }

    @PostMapping("/admin/invoices")
    public BillingDtos.InvoiceView create(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody BillingDtos.InvoiceCreate body) {

        access.requireStaff(me);
        List<BillingService.LineDraft> lines = body.lines().stream()
                .map(l -> new BillingService.LineDraft(
                        l.description(), l.quantity() <= 0 ? 1 : l.quantity(), l.unitCents()))
                .toList();
        Invoice invoice = billing.createInvoice(
                me, body.orgId(), body.projectId(), body.kind(), body.memo(), lines, body.dueAt());
        return BillingDtos.InvoiceView.of(invoice, true);
    }

    @PostMapping("/admin/invoices/{id}/send")
    public BillingDtos.InvoiceView send(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        access.requireStaff(me);
        return BillingDtos.InvoiceView.of(billing.sendInvoice(me, id), true);
    }

    @PostMapping("/admin/invoices/{id}/void")
    public BillingDtos.InvoiceView voidInvoice(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        access.requireStaff(me);
        return BillingDtos.InvoiceView.of(billing.voidInvoice(me, id), true);
    }

    private String planName(UUID planId) {
        return planId == null ? null : plans.findById(planId).map(Plan::getName).orElse(null);
    }
}
