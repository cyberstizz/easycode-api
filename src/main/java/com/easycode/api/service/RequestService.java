package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.ChangeOrder;
import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.RequestMessage;
import com.easycode.api.domain.RequestRead;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.BillingDisposition;
import com.easycode.api.domain.enums.ChangeOrderStatus;
import com.easycode.api.domain.enums.InvoiceKind;
import com.easycode.api.domain.enums.Priority;
import com.easycode.api.domain.enums.RequestStatus;
import com.easycode.api.domain.enums.RequestType;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ChangeOrderRepository;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.RequestMessageRepository;
import com.easycode.api.repo.RequestReadRepository;
import com.easycode.api.repo.RequestRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision 3 in code: one object for update requests, questions, new-project asks and bugs,
 * with the thread inside it and a billing disposition on the front.
 */
@Service
public class RequestService {

    private final RequestRepository requests;
    private final RequestMessageRepository messages;
    private final RequestReadRepository reads;
    private final ChangeOrderRepository changeOrders;
    private final OrganizationRepository orgs;
    private final UserRepository users;
    private final ContactRepository contacts;
    private final BillingService billing;
    private final EmailService email;
    private final AuditService audit;
    private final AppProperties props;

    public RequestService(
            RequestRepository requests,
            RequestMessageRepository messages,
            RequestReadRepository reads,
            ChangeOrderRepository changeOrders,
            OrganizationRepository orgs,
            UserRepository users,
            ContactRepository contacts,
            BillingService billing,
            EmailService email,
            AuditService audit,
            AppProperties props) {
        this.requests = requests;
        this.messages = messages;
        this.reads = reads;
        this.changeOrders = changeOrders;
        this.orgs = orgs;
        this.users = users;
        this.contacts = contacts;
        this.billing = billing;
        this.email = email;
        this.audit = audit;
        this.props = props;
    }

    public static final List<RequestStatus> OPEN_STATUSES =
            List.of(RequestStatus.NEW, RequestStatus.ACKNOWLEDGED, RequestStatus.IN_PROGRESS,
                    RequestStatus.NEEDS_CLIENT);

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<ClientRequest> list(
            AuthPrincipal me,
            UUID orgId,
            UUID projectId,
            RequestStatus status,
            RequestType type,
            UUID assigneeId,
            boolean openOnly) {

        UUID scopedOrg = me.isStaff() ? orgId : me.orgId();

        Specification<ClientRequest> spec = (root, q, cb) -> cb.conjunction();
        if (scopedOrg != null) {
            UUID o = scopedOrg;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("orgId"), o));
        }
        if (projectId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("projectId"), projectId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        }
        if (assigneeId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assigneeId"), assigneeId));
        }
        if (openOnly) {
            spec = spec.and((root, q, cb) -> root.get("status").in(OPEN_STATUSES));
        }
        return requests.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    @Transactional(readOnly = true)
    public List<RequestMessage> thread(AuthPrincipal me, UUID requestId) {
        return me.isStaff()
                ? messages.findByRequestIdOrderByCreatedAtAsc(requestId)
                : messages.findByRequestIdAndInternalOnlyFalseOrderByCreatedAtAsc(requestId);
    }

    @Transactional(readOnly = true)
    public List<ChangeOrder> changeOrdersFor(UUID requestId) {
        return changeOrders.findByRequestIdOrderByCreatedAtDesc(requestId);
    }

    @Transactional(readOnly = true)
    public boolean hasUnread(AuthPrincipal me, ClientRequest request) {
        Instant lastRead = reads.findByRequestIdAndUserId(request.getId(), me.userId())
                .map(RequestRead::getLastReadAt)
                .orElse(Instant.EPOCH);
        return request.getUpdatedAt() != null && request.getUpdatedAt().isAfter(lastRead);
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public ClientRequest create(
            AuthPrincipal me,
            UUID orgId,
            UUID projectId,
            RequestType type,
            String title,
            String body,
            Priority priority) {

        ClientRequest request = new ClientRequest();
        request.setOrgId(orgId);
        request.setProjectId(projectId);
        request.setCreatedBy(me.userId());
        request.setType(type == null ? RequestType.UPDATE : type);
        request.setTitle(title.trim());
        request.setPriority(priority == null ? Priority.NORMAL : priority);
        request.setStatus(RequestStatus.NEW);
        ClientRequest saved = requests.save(request);

        if (body != null && !body.isBlank()) {
            RequestMessage first = new RequestMessage();
            first.setRequestId(saved.getId());
            first.setAuthorId(me.userId());
            first.setBody(body.trim());
            messages.save(first);
        }
        markRead(me, saved.getId());

        String link = props.getBaseUrl() + "/requests/" + saved.getId();
        if (me.isClient()) {
            email.sendRequestReceived(me.email(), saved.getTitle(), link);
            String orgName = orgs.findById(orgId).map(Organization::getName).orElse("A client");
            String adminLink = props.getBaseUrl() + "/admin/requests/" + saved.getId();
            users.findByRoleIn(List.of(Role.ADMIN)).forEach(admin ->
                    email.sendNewRequestAlert(admin.getEmail(), orgName, saved.getTitle(), adminLink));
        }

        audit.record(me, "request.create", "request", saved.getId(),
                Map.of("type", saved.getType().name(), "title", saved.getTitle()));
        return saved;
    }

    @Transactional
    public RequestMessage addMessage(
            AuthPrincipal me, ClientRequest request, String body, boolean internalOnly) {

        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("Message can't be empty");
        }
        boolean internal = internalOnly && me.isStaff();

        RequestMessage message = new RequestMessage();
        message.setRequestId(request.getId());
        message.setAuthorId(me.userId());
        message.setBody(body.trim());
        message.setInternalOnly(internal);
        RequestMessage saved = messages.save(message);

        if (!internal) {
            if (me.isStaff()) {
                if (request.getFirstResponseAt() == null) {
                    request.setFirstResponseAt(Instant.now());
                }
                if (request.getStatus() == RequestStatus.NEW) {
                    request.setStatus(RequestStatus.ACKNOWLEDGED);
                }
            } else if (request.getStatus() == RequestStatus.NEEDS_CLIENT) {
                request.setStatus(RequestStatus.IN_PROGRESS);
            }
        }
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        markRead(me, request.getId());

        notifyCounterparty(me, request, internal);
        return saved;
    }

    private void notifyCounterparty(AuthPrincipal me, ClientRequest request, boolean internal) {
        if (internal) {
            return;
        }
        String link = props.getBaseUrl() + "/requests/" + request.getId();
        if (me.isStaff()) {
            contacts.findByOrgId(request.getOrgId()).stream()
                    .filter(c -> c.getUserId() != null)
                    .forEach(c -> email.send(
                            c.getEmail(),
                            "Reply on: " + request.getTitle(),
                            "<p>There's a new reply on your request. <a href=\"" + link + "\">Open it here</a>.</p>"));
        } else {
            String orgName = orgs.findById(request.getOrgId()).map(Organization::getName).orElse("A client");
            String adminLink = props.getBaseUrl() + "/admin/requests/" + request.getId();
            users.findByRoleIn(List.of(Role.ADMIN, Role.AGENT)).forEach(staff ->
                    email.sendNewRequestAlert(staff.getEmail(), orgName, request.getTitle(), adminLink));
        }
    }

    @Transactional
    public ClientRequest triage(
            AuthPrincipal me,
            ClientRequest request,
            RequestStatus status,
            BillingDisposition billingDisposition,
            UUID assigneeId,
            Priority priority,
            Instant dueAt) {

        List<String> changes = new ArrayList<>();
        if (status != null && status != request.getStatus()) {
            changes.add("status " + request.getStatus() + " -> " + status);
            request.setStatus(status);
            if (status == RequestStatus.DONE || status == RequestStatus.DECLINED) {
                request.setClosedAt(Instant.now());
            } else {
                request.setClosedAt(null);
            }
        }
        if (billingDisposition != null && billingDisposition != request.getBilling()) {
            changes.add("billing " + request.getBilling() + " -> " + billingDisposition);
            request.setBilling(billingDisposition);
        }
        if (assigneeId != null) {
            request.setAssigneeId(assigneeId);
            changes.add("assigned");
        }
        if (priority != null) {
            request.setPriority(priority);
        }
        if (dueAt != null) {
            request.setDueAt(dueAt);
        }
        ClientRequest saved = requests.save(request);
        audit.record(me, "request.triage", "request", request.getId(), Map.of("changes", changes));
        return saved;
    }

    @Transactional
    public void markRead(AuthPrincipal me, UUID requestId) {
        RequestRead read = reads.findByRequestIdAndUserId(requestId, me.userId())
                .orElseGet(() -> {
                    RequestRead fresh = new RequestRead();
                    fresh.setRequestId(requestId);
                    fresh.setUserId(me.userId());
                    return fresh;
                });
        read.setLastReadAt(Instant.now());
        reads.save(read);
    }

    // ---------------------------------------------------------- change orders

    @Transactional
    public ChangeOrder proposeChangeOrder(
            AuthPrincipal me, ClientRequest request, int amountCents, String description) {

        if (amountCents <= 0) {
            throw ApiException.badRequest("Amount must be greater than zero");
        }
        ChangeOrder order = new ChangeOrder();
        order.setRequestId(request.getId());
        order.setAmountCents(amountCents);
        order.setDescription(description);
        order.setCreatedBy(me.userId());
        ChangeOrder saved = changeOrders.save(order);

        request.setBilling(BillingDisposition.BILLABLE);
        request.setStatus(RequestStatus.NEEDS_CLIENT);
        requests.save(request);

        audit.record(me, "change_order.propose", "change_order", saved.getId(),
                Map.of("requestId", request.getId().toString(), "amountCents", amountCents));
        return saved;
    }

    /** One Approve button, by design. Approval raises the invoice immediately. */
    @Transactional
    public ChangeOrder approveChangeOrder(AuthPrincipal me, UUID changeOrderId) {
        ChangeOrder order = changeOrders.findById(changeOrderId)
                .orElseThrow(() -> ApiException.notFound("Change order"));
        ClientRequest request = requests.findById(order.getRequestId())
                .orElseThrow(() -> ApiException.notFound("Request"));

        if (!me.isStaff() && (me.orgId() == null || !me.orgId().equals(request.getOrgId()))) {
            throw ApiException.forbidden();
        }
        if (order.getStatus() != ChangeOrderStatus.PROPOSED) {
            throw ApiException.badRequest("That change order has already been actioned");
        }

        order.setStatus(ChangeOrderStatus.APPROVED);
        order.setApprovedAt(Instant.now());
        order.setApprovedBy(me.userId());

        Invoice invoice = billing.createInvoice(
                me,
                request.getOrgId(),
                request.getProjectId(),
                InvoiceKind.CHANGE_ORDER,
                "Change order: " + request.getTitle(),
                List.of(new BillingService.LineDraft(order.getDescription(), 1, order.getAmountCents())),
                null);
        billing.sendInvoice(me, invoice.getId());
        order.setInvoiceId(invoice.getId());
        ChangeOrder saved = changeOrders.save(order);

        request.setStatus(RequestStatus.IN_PROGRESS);
        requests.save(request);

        UserAccount approver = users.findById(me.userId()).orElse(null);
        audit.record(me, "change_order.approve", "change_order", order.getId(),
                Map.of(
                        "requestId", request.getId().toString(),
                        "amountCents", order.getAmountCents(),
                        "invoiceId", invoice.getId().toString(),
                        "approvedBy", approver == null ? me.email() : approver.getEmail()));
        return saved;
    }

    @Transactional
    public ChangeOrder declineChangeOrder(AuthPrincipal me, UUID changeOrderId) {
        ChangeOrder order = changeOrders.findById(changeOrderId)
                .orElseThrow(() -> ApiException.notFound("Change order"));
        ClientRequest request = requests.findById(order.getRequestId())
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (!me.isStaff() && (me.orgId() == null || !me.orgId().equals(request.getOrgId()))) {
            throw ApiException.forbidden();
        }
        order.setStatus(ChangeOrderStatus.DECLINED);
        ChangeOrder saved = changeOrders.save(order);
        audit.record(me, "change_order.decline", "change_order", order.getId());
        return saved;
    }
}
