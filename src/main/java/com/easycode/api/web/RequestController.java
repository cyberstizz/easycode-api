package com.easycode.api.web;

import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Project;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.RequestStatus;
import com.easycode.api.domain.enums.RequestType;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.RequestMessageRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.AssetService;
import com.easycode.api.service.RequestService;
import com.easycode.api.web.dto.AssetDtos;
import com.easycode.api.web.dto.RequestDtos;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class RequestController {

    private final RequestService requests;
    private final AssetService assets;
    private final AccessService access;
    private final OrganizationRepository orgs;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final RequestMessageRepository messages;

    public RequestController(
            RequestService requests,
            AssetService assets,
            AccessService access,
            OrganizationRepository orgs,
            ProjectRepository projects,
            UserRepository users,
            RequestMessageRepository messages) {
        this.requests = requests;
        this.assets = assets;
        this.access = access;
        this.orgs = orgs;
        this.projects = projects;
        this.users = users;
        this.messages = messages;
    }

    @GetMapping("/requests")
    public List<RequestDtos.RequestView> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean openOnly) {

        if (orgId != null) {
            access.requireOrg(me, orgId);
        }
        return requests.list(me, orgId, projectId, status, type, assigneeId, openOnly).stream()
                .map(r -> view(me, r))
                .toList();
    }

    @PostMapping("/requests")
    public RequestDtos.RequestView create(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody RequestDtos.RequestCreate body) {

        UUID orgId = access.resolveOrgId(me, body.orgId());
        access.requireOrg(me, orgId);
        if (body.projectId() != null) {
            access.project(me, body.projectId());
        }
        ClientRequest saved = requests.create(
                me, orgId, body.projectId(), body.type(), body.title(), body.body(), body.priority());
        return view(me, saved);
    }

    @GetMapping("/requests/{id}")
    public RequestDtos.RequestDetail detail(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {

        ClientRequest request = access.request(me, id);
        Map<UUID, String> names = new HashMap<>();
        users.findAll().forEach(u -> names.put(u.getId(), u.isStaff() ? "EasyCode" : u.getName()));

        RequestDtos.RequestDetail detail = new RequestDtos.RequestDetail(
                view(me, request),
                requests.thread(me, id).stream().map(m -> RequestDtos.MessageView.of(m, names)).toList(),
                assets.forRequest(me, id).stream().map(AssetDtos.AssetView::of).toList(),
                requests.changeOrdersFor(id).stream().map(RequestDtos.ChangeOrderView::of).toList());

        requests.markRead(me, id);
        return detail;
    }

    @PostMapping("/requests/{id}/messages")
    public RequestDtos.MessageView reply(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @Valid @RequestBody RequestDtos.MessageCreate body) {

        ClientRequest request = access.request(me, id);
        return RequestDtos.MessageView.of(
                requests.addMessage(me, request, body.body(), body.internalOnly()),
                Map.of(me.userId(), me.isStaff() ? "EasyCode" : me.name()));
    }

    @PatchMapping("/requests/{id}")
    public RequestDtos.RequestView triage(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody RequestDtos.TriageUpdate body) {

        access.requireStaff(me);
        ClientRequest request = access.request(me, id);
        return view(me, requests.triage(
                me, request, body.status(), body.billing(), body.assigneeId(), body.priority(), body.dueAt()));
    }

    @PostMapping("/requests/{id}/read")
    public Map<String, Object> markRead(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        access.request(me, id);
        requests.markRead(me, id);
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------- change orders

    @PostMapping("/requests/{id}/change-orders")
    public RequestDtos.ChangeOrderView propose(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @Valid @RequestBody RequestDtos.ChangeOrderCreate body) {

        access.requireStaff(me);
        ClientRequest request = access.request(me, id);
        return RequestDtos.ChangeOrderView.of(
                requests.proposeChangeOrder(me, request, body.amountCents(), body.description()));
    }

    /** One button, by design. Approving raises and sends the invoice in the same move. */
    @PostMapping("/change-orders/{id}/approve")
    public RequestDtos.ChangeOrderView approve(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        return RequestDtos.ChangeOrderView.of(requests.approveChangeOrder(me, id));
    }

    @PostMapping("/change-orders/{id}/decline")
    public RequestDtos.ChangeOrderView decline(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        return RequestDtos.ChangeOrderView.of(requests.declineChangeOrder(me, id));
    }

    // ----------------------------------------------------------------- shared

    private RequestDtos.RequestView view(AuthPrincipal me, ClientRequest r) {
        String orgName = orgs.findById(r.getOrgId()).map(Organization::getName).orElse(null);
        String projectName = r.getProjectId() == null
                ? null
                : projects.findById(r.getProjectId()).map(Project::getName).orElse(null);
        String assigneeName = r.getAssigneeId() == null
                ? null
                : users.findById(r.getAssigneeId()).map(UserAccount::getName).orElse(null);
        return RequestDtos.RequestView.of(
                r, orgName, projectName, assigneeName,
                messages.countByRequestId(r.getId()),
                requests.hasUnread(me, r));
    }
}
