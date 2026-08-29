package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Invite;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.InviteRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.security.Tokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgService {

    private final OrganizationRepository orgs;
    private final ContactRepository contacts;
    private final InviteRepository invites;
    private final UserRepository users;
    private final EmailService email;
    private final AuditService audit;
    private final AppProperties props;

    public OrgService(
            OrganizationRepository orgs,
            ContactRepository contacts,
            InviteRepository invites,
            UserRepository users,
            EmailService email,
            AuditService audit,
            AppProperties props) {
        this.orgs = orgs;
        this.contacts = contacts;
        this.invites = invites;
        this.users = users;
        this.email = email;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Page<Organization> list(String query, Pageable pageable) {
        return (query == null || query.isBlank())
                ? orgs.findAll(pageable)
                : orgs.search(query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Organization get(UUID id) {
        return orgs.findById(id).orElseThrow(() -> ApiException.notFound("Organization"));
    }

    @Transactional(readOnly = true)
    public List<Contact> contactsFor(UUID orgId) {
        return contacts.findByOrgId(orgId);
    }

    @Transactional
    public Organization create(AuthPrincipal actor, Organization draft) {
        Organization saved = orgs.save(draft);
        audit.record(actor, "org.create", "organization", saved.getId(),
                Map.of("name", saved.getName(), "tier", saved.getDealTier().name()));
        return saved;
    }

    @Transactional
    public Organization update(AuthPrincipal actor, UUID id, Organization patch) {
        Organization org = get(id);
        if (patch.getName() != null) org.setName(patch.getName());
        if (patch.getIndustry() != null) org.setIndustry(patch.getIndustry());
        if (patch.getWebsite() != null) org.setWebsite(patch.getWebsite());
        if (patch.getPhone() != null) org.setPhone(patch.getPhone());
        if (patch.getAddress() != null) org.setAddress(patch.getAddress());
        if (patch.getNotes() != null) org.setNotes(patch.getNotes());
        if (patch.getDealTier() != null) org.setDealTier(patch.getDealTier());
        if (patch.getStatus() != null) org.setStatus(patch.getStatus());
        Organization saved = orgs.save(org);
        audit.record(actor, "org.update", "organization", id);
        return saved;
    }

    @Transactional
    public Contact addContact(AuthPrincipal actor, UUID orgId, Contact draft) {
        get(orgId);
        String addr = draft.getEmail().trim().toLowerCase();
        contacts.findByOrgIdAndEmailIgnoreCase(orgId, addr).ifPresent(c -> {
            throw ApiException.conflict("That contact already exists for this client");
        });
        draft.setOrgId(orgId);
        draft.setEmail(addr);
        Contact saved = contacts.save(draft);
        audit.record(actor, "contact.create", "contact", saved.getId(), Map.of("orgId", orgId.toString()));
        return saved;
    }

    /**
     * The flow that replaces signup: admin invites a contact, Resend delivers the link,
     * the client picks their own password.
     */
    /** The invite plus its raw token — the token is never persisted, only its hash. */
    public record IssuedInvite(Invite invite, String rawToken) {}

    @Transactional
    public IssuedInvite invite(AuthPrincipal actor, UUID contactId) {
        Contact contact = contacts.findById(contactId).orElseThrow(() -> ApiException.notFound("Contact"));
        Organization org = get(contact.getOrgId());

        // Catch the collision here rather than at accept time: the admin is standing in
        // front of the screen now, the client is not.
        users.findByEmailIgnoreCase(contact.getEmail()).ifPresent(existing -> {
            if (existing.getRole() != null && existing.getRole() != Role.CLIENT) {
                throw ApiException.conflict(
                        "That address is a staff account — use a different email for this contact");
            }
            if (existing.getOrgId() != null && !existing.getOrgId().equals(contact.getOrgId())) {
                throw ApiException.conflict("That address already belongs to a different client");
            }
        });

        String raw = Tokens.random();
        Invite invite = new Invite();
        invite.setContactId(contact.getId());
        invite.setEmail(contact.getEmail());
        invite.setTokenHash(Tokens.hash(raw));
        invite.setInvitedBy(actor.userId());
        invite.setExpiresAt(Instant.now().plus(props.getInvite().getTtlHours(), ChronoUnit.HOURS));
        Invite saved = invites.save(invite);

        email.sendInvite(
                contact.getEmail(),
                contact.getName(),
                org.getName(),
                props.getBaseUrl() + "/accept-invite?token=" + raw);

        audit.record(actor, "contact.invite", "contact", contact.getId(),
                Map.of("email", contact.getEmail()));
        return new IssuedInvite(saved, raw);
    }
}