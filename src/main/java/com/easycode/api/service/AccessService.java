package com.easycode.api.service;

import com.easycode.api.domain.Asset;
import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Project;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.AssetRepository;
import com.easycode.api.repo.InvoiceRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.RequestRepository;
import com.easycode.api.security.AuthPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decision 1: tenancy is enforced here, in Java, where it can be read, tested and logged —
 * not in an RLS policy. Every entity fetch for a client goes through one of these.
 */
@Service
public class AccessService {

    private final OrganizationRepository orgs;
    private final ProjectRepository projects;
    private final RequestRepository requests;
    private final AssetRepository assets;
    private final InvoiceRepository invoices;

    public AccessService(
            OrganizationRepository orgs,
            ProjectRepository projects,
            RequestRepository requests,
            AssetRepository assets,
            InvoiceRepository invoices) {
        this.orgs = orgs;
        this.projects = projects;
        this.requests = requests;
        this.assets = assets;
        this.invoices = invoices;
    }

    /** Staff see everything; a client only ever sees their own org. */
    public void requireOrg(AuthPrincipal me, UUID orgId) {
        if (me.isStaff()) {
            return;
        }
        if (me.orgId() == null || !me.orgId().equals(orgId)) {
            throw ApiException.forbidden();
        }
    }

    public Organization org(AuthPrincipal me, UUID orgId) {
        Organization org = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization"));
        requireOrg(me, org.getId());
        return org;
    }

    public Project project(AuthPrincipal me, UUID projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        requireOrg(me, p.getOrgId());
        return p;
    }

    public ClientRequest request(AuthPrincipal me, UUID requestId) {
        ClientRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request"));
        requireOrg(me, r.getOrgId());
        return r;
    }

    public Asset asset(AuthPrincipal me, UUID assetId) {
        Asset a = assets.findById(assetId).orElseThrow(() -> ApiException.notFound("File"));
        requireOrg(me, a.getOrgId());
        if (!me.isStaff() && a.getVisibility() == com.easycode.api.domain.enums.AssetVisibility.INTERNAL) {
            throw ApiException.forbidden();
        }
        return a;
    }

    public Invoice invoice(AuthPrincipal me, UUID invoiceId) {
        Invoice i = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        requireOrg(me, i.getOrgId());
        return i;
    }

    public void requireStaff(AuthPrincipal me) {
        if (!me.isStaff()) {
            throw ApiException.forbidden();
        }
    }

    public void requireAdmin(AuthPrincipal me) {
        if (!me.isAdmin()) {
            throw ApiException.forbidden();
        }
    }

    /** For staff endpoints that accept an explicit orgId, and client endpoints that must not. */
    public UUID resolveOrgId(AuthPrincipal me, UUID requestedOrgId) {
        if (me.isStaff()) {
            if (requestedOrgId == null) {
                throw ApiException.badRequest("orgId is required");
            }
            return requestedOrgId;
        }
        return me.orgId();
    }
}
