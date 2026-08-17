package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.InvoiceLine;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Payment;
import com.easycode.api.domain.StripeEvent;
import com.easycode.api.domain.Subscription;
import com.easycode.api.domain.enums.InvoiceKind;
import com.easycode.api.domain.enums.InvoiceStatus;
import com.easycode.api.domain.enums.PaymentStatus;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.InvoiceRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.PaymentRepository;
import com.easycode.api.repo.StripeEventRepository;
import com.easycode.api.repo.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one piece of payment code worth writing carefully.
 * Signature-verified, idempotent (Stripe retries), and the only place an invoice is marked paid.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeEventRepository events;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final OrganizationRepository orgs;
    private final AuditService audit;
    private final AppProperties props;

    public StripeWebhookService(
            StripeEventRepository events,
            PaymentRepository payments,
            InvoiceRepository invoices,
            SubscriptionRepository subscriptions,
            OrganizationRepository orgs,
            AuditService audit,
            AppProperties props) {
        this.events = events;
        this.payments = payments;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.orgs = orgs;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public void handle(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, props.getStripe().getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with a bad signature");
            throw ApiException.badRequest("Invalid signature");
        }

        // Stripe retries on any non-2xx, so every handler below must be safe to run twice.
        if (events.existsById(event.getId())) {
            log.debug("Stripe event {} already processed — ignoring the retry", event.getId());
            return;
        }

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded" -> onPaymentSucceeded(event);
                case "payment_intent.payment_failed" -> onPaymentFailed(event);
                case "invoice.paid" -> onStripeInvoicePaid(event);
                case "customer.subscription.created",
                     "customer.subscription.updated",
                     "customer.subscription.deleted" -> onSubscriptionChanged(event);
                default -> log.debug("Ignoring Stripe event type {}", event.getType());
            }
        } finally {
            StripeEvent row = new StripeEvent();
            row.setId(event.getId());
            row.setType(event.getType());
            events.save(row);
        }
    }

    private Optional<StripeObject> object(Event event) {
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();
        if (deserialized.isPresent()) {
            return deserialized;
        }
        try {
            // API-version drift between our SDK and the account — fall back rather than drop the event
            return Optional.ofNullable(event.getDataObjectDeserializer().deserializeUnsafe());
        } catch (Exception e) {
            log.error("Could not deserialize Stripe event {} of type {}", event.getId(), event.getType(), e);
            return Optional.empty();
        }
    }

    private void onPaymentSucceeded(Event event) {
        object(event).filter(PaymentIntent.class::isInstance).map(PaymentIntent.class::cast).ifPresent(intent -> {
            Payment payment = payments.findByStripePaymentIntentId(intent.getId()).orElse(null);
            if (payment == null) {
                log.warn("payment_intent.succeeded for unknown intent {}", intent.getId());
                return;
            }
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                return;
            }
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setMethod(intent.getPaymentMethod());
            payments.save(payment);

            if (payment.getInvoiceId() != null) {
                invoices.findById(payment.getInvoiceId()).ifPresent(invoice -> {
                    int paid = invoice.getAmountPaidCents() + payment.getAmountCents();
                    invoice.setAmountPaidCents(Math.min(paid, invoice.getAmountCents()));
                    if (invoice.getAmountPaidCents() >= invoice.getAmountCents()) {
                        invoice.setStatus(InvoiceStatus.PAID);
                        invoice.setPaidAt(Instant.now());
                    }
                    invoices.save(invoice);
                    audit.record(null, "invoice.paid", "invoice", invoice.getId(),
                            Map.of("number", invoice.getNumber(),
                                    "amountCents", payment.getAmountCents(),
                                    "paymentIntentId", intent.getId()));
                });
            }
            log.info("Payment succeeded: {} for {}", intent.getId(), BillingService.money(payment.getAmountCents()));
        });
    }

    private void onPaymentFailed(Event event) {
        object(event).filter(PaymentIntent.class::isInstance).map(PaymentIntent.class::cast).ifPresent(intent -> {
            payments.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureMessage(
                        intent.getLastPaymentError() == null ? null : intent.getLastPaymentError().getMessage());
                payments.save(payment);
                log.warn("Payment failed: {} — {}", intent.getId(), payment.getFailureMessage());
            });
        });
    }

    /** Subscription renewals are billed by Stripe; we mirror them so the portal shows one list. */
    private void onStripeInvoicePaid(Event event) {
        object(event)
                .filter(com.stripe.model.Invoice.class::isInstance)
                .map(com.stripe.model.Invoice.class::cast)
                .ifPresent(stripeInvoice -> {
                    if (stripeInvoice.getId() != null
                            && invoices.findByStripeInvoiceId(stripeInvoice.getId()).isPresent()) {
                        return;
                    }
                    Organization org = orgs.findByStripeCustomerId(stripeInvoice.getCustomer()).orElse(null);
                    if (org == null) {
                        log.warn("Stripe invoice {} for unknown customer {}",
                                stripeInvoice.getId(), stripeInvoice.getCustomer());
                        return;
                    }
                    long amount = stripeInvoice.getAmountPaid() == null ? 0L : stripeInvoice.getAmountPaid();

                    Invoice mirrored = new Invoice();
                    mirrored.setOrgId(org.getId());
                    mirrored.setKind(InvoiceKind.SUBSCRIPTION);
                    mirrored.setNumber(stripeInvoice.getNumber() != null
                            ? stripeInvoice.getNumber()
                            : "STRIPE-" + stripeInvoice.getId());
                    mirrored.setAmountCents((int) amount);
                    mirrored.setAmountPaidCents((int) amount);
                    mirrored.setStatus(InvoiceStatus.PAID);
                    mirrored.setPaidAt(Instant.now());
                    mirrored.setSentAt(Instant.now());
                    mirrored.setStripeInvoiceId(stripeInvoice.getId());
                    mirrored.setMemo("Maintenance plan");

                    InvoiceLine line = new InvoiceLine();
                    line.setDescription("Monthly maintenance");
                    line.setQuantity(BigDecimal.ONE);
                    line.setUnitCents((int) amount);
                    line.setPosition((short) 0);
                    mirrored.getLines().add(line);

                    invoices.save(mirrored);
                    audit.record(null, "invoice.subscription.paid", "invoice", mirrored.getId(),
                            Map.of("stripeInvoiceId", String.valueOf(stripeInvoice.getId()), "amountCents", amount));
                });
    }

    private void onSubscriptionChanged(Event event) {
        object(event)
                .filter(com.stripe.model.Subscription.class::isInstance)
                .map(com.stripe.model.Subscription.class::cast)
                .ifPresent(stripeSub -> {
                    Subscription sub = subscriptions.findByStripeSubId(stripeSub.getId()).orElse(null);
                    if (sub == null) {
                        Organization org = orgs.findByStripeCustomerId(stripeSub.getCustomer()).orElse(null);
                        if (org == null) {
                            log.warn("Subscription event for unknown customer {}", stripeSub.getCustomer());
                            return;
                        }
                        sub = new Subscription();
                        sub.setOrgId(org.getId());
                        sub.setStripeSubId(stripeSub.getId());
                    }
                    sub.setStatus(BillingService.mapSubscriptionStatus(stripeSub.getStatus()));
                    if (stripeSub.getCancelAt() != null) {
                        sub.setCancelAt(Instant.ofEpochSecond(stripeSub.getCancelAt()));
                    }
                    subscriptions.save(sub);
                    log.info("Subscription {} is now {}", stripeSub.getId(), sub.getStatus());
                });
    }
}
