package com.easycode.api.web;

import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Organization;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.config.AppProperties;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.OrgService;
import com.easycode.api.web.dto.OrgDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/organizations")
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class OrgController {

    private final OrgService orgs;
    private final AccessService access;
    private final AppProperties props;

    public OrgController(OrgService orgs, AccessService access, AppProperties props) {
        this.orgs = orgs;
        this.access = access;
        this.props = props;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Page<Organization> found = orgs.list(
                q, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of(
                "items", found.getContent().stream().map(OrgDtos.OrgView::summary).toList(),
                "page", found.getNumber(),
                "totalPages", found.getTotalPages(),
                "totalItems", found.getTotalElements());
    }

    @GetMapping("/{id}")
    public OrgDtos.OrgView get(@PathVariable UUID id) {
        return OrgDtos.OrgView.of(orgs.get(id), orgs.contactsFor(id));
    }

    @PostMapping
    public OrgDtos.OrgView create(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody OrgDtos.OrgUpsert body) {
        Organization draft = new Organization();
        apply(draft, body);
        return OrgDtos.OrgView.summary(orgs.create(me, draft));
    }

    @PatchMapping("/{id}")
    public OrgDtos.OrgView update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody OrgDtos.OrgUpsert body) {
        Organization patch = new Organization();
        apply(patch, body);
        return OrgDtos.OrgView.of(orgs.update(me, id, patch), orgs.contactsFor(id));
    }

    @GetMapping("/{id}/contacts")
    public List<OrgDtos.ContactView> contacts(@PathVariable UUID id) {
        return orgs.contactsFor(id).stream().map(OrgDtos.ContactView::of).toList();
    }

    @PostMapping("/{id}/contacts")
    public OrgDtos.ContactView addContact(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @Valid @RequestBody OrgDtos.ContactUpsert body) {

        Contact draft = new Contact();
        draft.setName(body.name());
        draft.setEmail(body.email());
        draft.setPhone(body.phone());
        draft.setRole(body.role());
        draft.setPrimaryContact(body.isPrimary());
        return OrgDtos.ContactView.of(orgs.addContact(me, id, draft));
    }

    /**
     * Replaces signup: this is how a client gets an account.
     *
     * <p>When email sending is off, the accept URL comes back in the response so it
     * can be delivered by hand. With Resend enabled the link is omitted — at that
     * point the only copy lives in the client's inbox, which is the point.
     */
    @PostMapping("/contacts/{contactId}/invite")
    public Map<String, Object> invite(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID contactId) {
        access.requireStaff(me);
        var issued = orgs.invite(me, contactId);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ok", true);
        body.put("email", issued.invite().getEmail());
        body.put("expiresAt", issued.invite().getExpiresAt());
        body.put("emailSent", props.getResend().isEnabled());
        if (!props.getResend().isEnabled()) {
            body.put("acceptUrl", props.getBaseUrl() + "/accept-invite?token=" + issued.rawToken());
        }
        return body;
    }

    private void apply(Organization org, OrgDtos.OrgUpsert body) {
        org.setName(body.name());
        org.setIndustry(body.industry());
        org.setWebsite(body.website());
        org.setPhone(body.phone());
        org.setAddress(body.address());
        org.setNotes(body.notes());
        if (body.dealTier() != null) {
            org.setDealTier(body.dealTier());
        }
        if (body.status() != null) {
            org.setStatus(body.status());
        }
    }
}