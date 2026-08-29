package com.easycode.api.service;

import com.easycode.api.domain.Lead;
import com.easycode.api.domain.Organization;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.AssetRepository;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.InvoiceRepository;
import com.easycode.api.repo.LeadRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.RequestRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting a client, deliberately kept out of OrgService.
 *
 * <p>This is the only irreversible operation in the admin, so it gets its own file where
 * the whole story is readable in one screen. Three gates before anything is destroyed:
 * the actor must be ADMIN, they must retype their own password, and they must retype the
 * client's name. Agents cannot reach it at all.
 *
 * <p>The row deletion itself is one statement — V1__init.sql already cascades from
 * organizations to users, contacts, projects, project_stages, requests, request_messages,
 * assets, subscriptions, invoices, invoice_lines and payments. The work here is everything
 * the database can't do for us: removing the objects out of R2 first, and deciding what
 * happens to the lead that produced this client (leads.org_id is ON DELETE SET NULL, so by
 * default the lead survives, detached).
 */
@Service
public class OrgDeletionService {

    private static final Logger log = LoggerFactory.getLogger(OrgDeletionService.class);

    private final OrganizationRepository orgs;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final RequestRepository requests;
    private final AssetRepository assets;
    private final InvoiceRepository invoices;
    private final LeadRepository leads;
    private final AssetService assetService;
    private final AuthService auth;
    private final AuditService audit;

    public OrgDeletionService(
            OrganizationRepository orgs,
            ContactRepository contacts,
            UserRepository users,
            ProjectRepository projects,
            RequestRepository requests,
            AssetRepository assets,
            InvoiceRepository invoices,
            LeadRepository leads,
            AssetService assetService,
            AuthService auth,
            AuditService audit) {
        this.orgs = orgs;
        this.contacts = contacts;
        this.users = users;
        this.projects = projects;
        this.requests = requests;
        this.assets = assets;
        this.invoices = invoices;
        this.leads = leads;
        this.assetService = assetService;
        this.auth = auth;
        this.audit = audit;
    }

    /** What the confirmation dialog shows. Counts only — nothing here is sensitive. */
    public record Preview(
            UUID id,
            String name,
            long contacts,
            long logins,
            long projects,
            long requests,
            long files,
            long invoices,
            long leads) {}

    /** What actually went. Returned so the UI can say something true afterwards. */
    public record Result(
            String name,
            long projects,
            long requests,
            long files,
            long invoices,
            int objectsRemoved,
            long leadsDeleted,
            long leadsDetached) {}

    @Transactional(readOnly = true)
    public Preview preview(UUID orgId) {
        Organization org = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));
        return new Preview(
                org.getId(),
                org.getName(),
                contacts.countByOrgId(orgId),
                users.countByOrgId(orgId),
                projects.countByOrgId(orgId),
                requests.countByOrgId(orgId),
                assets.countByOrgId(orgId),
                invoices.countByOrgId(orgId),
                leads.countByOrgId(orgId));
    }

    /**
     * @param confirmName the client's name, retyped. Guards against deleting the row you
     *     were merely looking at — the id in the URL is not enough of a decision.
     * @param deleteLinkedLeads true wipes the originating lead too. Right for test records
     *     you are cleaning up; wrong for a real client who churned, where the lead is the
     *     only history of how the deal was won.
     */
    @Transactional
    public Result delete(
            AuthPrincipal actor,
            UUID orgId,
            String password,
            String confirmName,
            boolean deleteLinkedLeads) {

        Organization org = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));

        // Gate 2 of 3 — gate 1 (ADMIN) is asserted by the controller before we get here.
        auth.requireCurrentPassword(actor, password);

        // Gate 3 — the typed name has to match.
        if (confirmName == null || !confirmName.trim().equalsIgnoreCase(org.getName().trim())) {
            throw ApiException.badRequest("The name you typed doesn't match this client");
        }

        Preview before = preview(orgId);

        // Bytes first. If the row goes and this fails, we lose the keys and the objects
        // are unreachable forever; this order means the worst case is a logged orphan.
        int objectsRemoved = assetService.purgeOrgObjects(orgId);

        long leadsDeleted = 0;
        long leadsDetached = 0;
        List<Lead> linked = leads.findByOrgId(orgId);
        if (deleteLinkedLeads) {
            leads.deleteAll(linked);
            leadsDeleted = linked.size();
        } else {
            // The FK is ON DELETE SET NULL, so the database detaches these for us.
            leadsDetached = linked.size();
        }

        // Record the audit line BEFORE the delete: audit_log.actor_id is ON DELETE SET NULL
        // against users, and the log row itself has no FK to organizations, so it survives.
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("name", org.getName());
        detail.put("tier", org.getDealTier() == null ? "none" : org.getDealTier().name());
        detail.put("projects", String.valueOf(before.projects()));
        detail.put("requests", String.valueOf(before.requests()));
        detail.put("invoices", String.valueOf(before.invoices()));
        detail.put("files", String.valueOf(before.files()));
        detail.put("objectsRemoved", String.valueOf(objectsRemoved));
        detail.put("leadsDeleted", String.valueOf(leadsDeleted));
        audit.record(actor, "org.delete", "organization", orgId, detail);

        orgs.delete(org);
        log.warn("Organization {} ({}) deleted by {}", org.getName(), orgId, actor.email());

        return new Result(
                org.getName(),
                before.projects(),
                before.requests(),
                before.files(),
                before.invoices(),
                objectsRemoved,
                leadsDeleted,
                leadsDetached);
    }
}