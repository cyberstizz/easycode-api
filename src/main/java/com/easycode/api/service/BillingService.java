package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.InvoiceLine;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Payment;
import com.easycode.api.domain.Plan;
import com.easycode.api.domain.Subscription;
import com.easycode.api.domain.enums.InvoiceKind;
import com.easycode.api.domain.enums.InvoiceStatus;
import com.easycode.api.domain.enums.PaymentStatus;
import com.easycode.api.domain.enums.SubscriptionStatus;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.InvoiceRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.PaymentRepository;
import com.easycode.api.repo.PlanRepository;
import com.easycode.api.repo.SubscriptionRepository;
import com.easycode.api.security.AuthPrincipal;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision 4, as Charles overrode it: the payment form is IN-APP (Stripe Payment Element),
 * not a hosted checkout page. Stripe is still the processor and still holds the card data —
 * we only ever hand the browser a client_secret, so PCI scope stays at SAQ A.
 *
 * One-off money (deposits, milestones, change orders) = our invoice + our PaymentIntent.
 * Recurring money (the $50/mo maintenance backbone) = SetupIntent, then a Stripe Subscription.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final OrganizationRepository orgs;
    private final ContactRepository contacts;
    private final EmailService email;
    private final AuditService audit;
    private final AppProperties props;

    public BillingService(
            InvoiceRepository invoices,
            PaymentRepository payments,
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            OrganizationRepository orgs,
            ContactRepository contacts,
            EmailService email,
            AuditService audit,
            AppProperties props) {
        this.invoices = invoices;
        this.payments = payments;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.orgs = orgs;
        this.contacts = contacts;
        this.email = email;
        this.audit = audit;
        this.props = props;
    }

    public record LineDraft(String description, double quantity, int unitCents) {}

    public record IntentResponse(String clientSecret, String intentId, int amountCents) {}

    // --------------------------------------------------------------- invoices

    @Transactional(readOnly = true)
    public List<Invoice> forOrg(UUID orgId) {
        return invoices.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> allRecent() {
        return invoices.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public long outstandingCents(UUID orgId) {
        return invoices.outstandingCentsForOrg(orgId);
    }

    @Transactional(readOnly = true)
    public List<Plan> activePlans() {
        return plans.findByActiveTrueOrderByPriceCentsAsc();
    }

    @Transactional(readOnly = true)
    public List<Subscription> subscriptionsFor(UUID orgId) {
        return subscriptions.findByOrgId(orgId);
    }

    @Transactional
    public Invoice createInvoice(
            AuthPrincipal actor,
            UUID orgId,
            UUID projectId,
            InvoiceKind kind,
            String memo,
            List<LineDraft> lineDrafts,
            Instant dueAt) {

        if (lineDrafts == null || lineDrafts.isEmpty()) {
            throw ApiException.badRequest("An invoice needs at least one line");
        }

        Invoice invoice = new Invoice();
        invoice.setOrgId(orgId);
        invoice.setProjectId(projectId);
        invoice.setKind(kind == null ? InvoiceKind.ONE_OFF : kind);
        invoice.setMemo(memo);
        invoice.setNumber(nextNumber());
        invoice.setDueAt(dueAt == null ? Instant.now().plus(14, ChronoUnit.DAYS) : dueAt);

        List<InvoiceLine> lines = new ArrayList<>();
        int total = 0;
        short position = 0;
        for (LineDraft draft : lineDrafts) {
            InvoiceLine line = new InvoiceLine();
            line.setDescription(draft.description());
            line.setQuantity(BigDecimal.valueOf(draft.quantity()));
            line.setUnitCents(draft.unitCents());
            line.setPosition(position++);
            total += line.totalCents();
            lines.add(line);
        }
        invoice.setAmountCents(total);
        invoice.getLines().addAll(lines);

        Invoice saved = invoices.save(invoice);
        audit.record(actor, "invoice.create", "invoice", saved.getId(),
                Map.of("number", saved.getNumber(), "amountCents", total, "kind", saved.getKind().name()));
        return saved;
    }

    @Transactional
    public Invoice sendInvoice(AuthPrincipal actor, UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw ApiException.badRequest("That invoice is already paid");
        }
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setSentAt(Instant.now());
        Invoice saved = invoices.save(invoice);

        String link = props.getBaseUrl() + "/billing/invoices/" + invoice.getId();
        contacts.findByOrgId(invoice.getOrgId()).stream()
                .filter(Contact::isPrimaryContact)
                .findFirst()
                .or(() -> contacts.findByOrgId(invoice.getOrgId()).stream().findFirst())
                .ifPresent(contact -> email.sendInvoiceSent(
                        contact.getEmail(), invoice.getNumber(), money(invoice.getAmountCents()), link));

        audit.record(actor, "invoice.send", "invoice", invoice.getId(), Map.of("number", invoice.getNumber()));
        return saved;
    }

    @Transactional
    public Invoice voidInvoice(AuthPrincipal actor, UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        invoice.setStatus(InvoiceStatus.VOID);
        Invoice saved = invoices.save(invoice);
        audit.record(actor, "invoice.void", "invoice", invoiceId);
        return saved;
    }

    /** Sequential, human-readable, and what shows on the client's card statement memo. */
    private String nextNumber() {
        String prefix = "EC-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        String max = invoices.maxNumberWithPrefix(prefix);
        int next = 1;
        if (max != null && !max.isBlank()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("Unparseable invoice number {} — restarting the sequence", max);
            }
        }
        return prefix + String.format("%04d", next);
    }

    // ------------------------------------------------------- in-app payments

    /**
     * Powers the in-app Payment Element. The browser confirms with this client_secret;
     * money is only ever considered received when the webhook says so.
     */
    @Transactional
    public IntentResponse payInvoice(AuthPrincipal me, Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw ApiException.badRequest("That invoice is already paid");
        }
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw ApiException.badRequest("That invoice was voided");
        }
        int amount = invoice.balanceCents();
        if (amount <= 0) {
            throw ApiException.badRequest("There is nothing left to pay on that invoice");
        }

        try {
            Organization org = orgs.findById(invoice.getOrgId())
                    .orElseThrow(() -> ApiException.notFound("Organization"));
            String customerId = ensureCustomer(org);

            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount);
            params.put("currency", props.getStripe().getCurrency());
            params.put("customer", customerId);
            params.put("description", "EasyCode " + invoice.getNumber());
            params.put("automatic_payment_methods", Map.of("enabled", true));
            params.put("metadata", Map.of(
                    "invoiceId", invoice.getId().toString(),
                    "invoiceNumber", invoice.getNumber(),
                    "orgId", invoice.getOrgId().toString()));

            PaymentIntent intent = PaymentIntent.create(params);

            Payment payment = payments.findByStripePaymentIntentId(intent.getId()).orElseGet(Payment::new);
            payment.setOrgId(invoice.getOrgId());
            payment.setInvoiceId(invoice.getId());
            payment.setStripePaymentIntentId(intent.getId());
            payment.setAmountCents(amount);
            payment.setStatus(PaymentStatus.REQUIRES_PAYMENT);
            payments.save(payment);

            audit.record(me, "payment.intent", "invoice", invoice.getId(),
                    Map.of("paymentIntentId", intent.getId(), "amountCents", amount));
            return new IntentResponse(intent.getClientSecret(), intent.getId(), amount);
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent failed for invoice {}", invoice.getNumber(), e);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "stripe_error",
                    "We couldn't reach the payment processor. Try again in a moment.");
        }
    }

    /** Step one of the maintenance plan: collect a card in-app without charging it yet. */
    @Transactional
    public IntentResponse setupIntent(AuthPrincipal me, UUID orgId) {
        try {
            Organization org = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));
            SetupIntent intent = SetupIntent.create(Map.of(
                    "customer", ensureCustomer(org),
                    "usage", "off_session",
                    "automatic_payment_methods", Map.of("enabled", true),
                    "metadata", Map.of("orgId", orgId.toString())));
            return new IntentResponse(intent.getClientSecret(), intent.getId(), 0);
        } catch (StripeException e) {
            log.error("Stripe SetupIntent failed for org {}", orgId, e);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "stripe_error",
                    "We couldn't reach the payment processor. Try again in a moment.");
        }
    }

    /** Step two: the payment method collected above becomes the subscription's default. */
    @Transactional
    public Subscription startSubscription(
            AuthPrincipal me, UUID orgId, UUID planId, String paymentMethodId, Short termMonths) {

        Plan plan = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Plan"));
        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            throw ApiException.badRequest(
                    "That plan has no Stripe price attached yet — set stripe_price_id on the plan first");
        }
        Organization org = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));

        try {
            String customerId = ensureCustomer(org);

            if (paymentMethodId != null && !paymentMethodId.isBlank()) {
                com.stripe.model.PaymentMethod method = com.stripe.model.PaymentMethod.retrieve(paymentMethodId);
                method.attach(Map.of("customer", customerId));
                Customer.retrieve(customerId)
                        .update(Map.of("invoice_settings", Map.of("default_payment_method", paymentMethodId)));
            }

            Map<String, Object> params = new HashMap<>();
            params.put("customer", customerId);
            params.put("items", List.of(Map.of("price", plan.getStripePriceId())));
            params.put("metadata", Map.of(
                    "orgId", orgId.toString(),
                    "planId", planId.toString(),
                    "termMonths", termMonths == null ? "" : termMonths.toString()));
            if (paymentMethodId != null && !paymentMethodId.isBlank()) {
                params.put("default_payment_method", paymentMethodId);
            }

            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.create(params);

            Subscription sub = subscriptions.findByStripeSubId(stripeSub.getId()).orElseGet(Subscription::new);
            sub.setOrgId(orgId);
            sub.setPlanId(planId);
            sub.setStripeSubId(stripeSub.getId());
            sub.setTermMonths(termMonths);
            sub.setStatus(mapSubscriptionStatus(stripeSub.getStatus()));
            Subscription saved = subscriptions.save(sub);

            audit.record(me, "subscription.start", "subscription", saved.getId(),
                    Map.of("orgId", orgId.toString(), "planId", planId.toString(),
                            "termMonths", termMonths == null ? "none" : termMonths.toString()));
            return saved;
        } catch (StripeException e) {
            log.error("Stripe subscription failed for org {}", orgId, e);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "stripe_error",
                    "We couldn't start that plan. Try again in a moment.");
        }
    }

    public String ensureCustomer(Organization org) throws StripeException {
        if (org.getStripeCustomerId() != null && !org.getStripeCustomerId().isBlank()) {
            return org.getStripeCustomerId();
        }
        String contactEmail = contacts.findByOrgId(org.getId()).stream()
                .filter(Contact::isPrimaryContact)
                .findFirst()
                .or(() -> contacts.findByOrgId(org.getId()).stream().findFirst())
                .map(Contact::getEmail)
                .orElse(null);

        Map<String, Object> params = new HashMap<>();
        params.put("name", org.getName());
        params.put("metadata", Map.of("orgId", org.getId().toString()));
        if (contactEmail != null) {
            params.put("email", contactEmail);
        }
        Customer customer = Customer.create(params);
        org.setStripeCustomerId(customer.getId());
        orgs.save(org);
        return customer.getId();
    }

    public static SubscriptionStatus mapSubscriptionStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return SubscriptionStatus.INCOMPLETE;
        }
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "unpaid" -> SubscriptionStatus.UNPAID;
            case "trialing" -> SubscriptionStatus.TRIALING;
            default -> SubscriptionStatus.INCOMPLETE;
        };
    }

    public static String money(int cents) {
        return "$" + BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
