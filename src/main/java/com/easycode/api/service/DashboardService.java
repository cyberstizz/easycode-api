package com.easycode.api.service;

import com.easycode.api.domain.Asset;
import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.Invoice;
import com.easycode.api.domain.Lead;
import com.easycode.api.domain.Project;
import com.easycode.api.domain.enums.InvoiceStatus;
import com.easycode.api.domain.enums.LeadStatus;
import com.easycode.api.domain.enums.RequestStatus;
import com.easycode.api.domain.enums.UploadState;
import com.easycode.api.repo.AssetRepository;
import com.easycode.api.repo.InvoiceRepository;
import com.easycode.api.repo.LeadRepository;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.RequestRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Two action surfaces: the client's portal home and the admin's daily queue. */
@Service
public class DashboardService {

    private final RequestRepository requests;
    private final ProjectRepository projects;
    private final InvoiceRepository invoices;
    private final AssetRepository assets;
    private final LeadRepository leads;
    private final RequestService requestService;

    public DashboardService(
            RequestRepository requests,
            ProjectRepository projects,
            InvoiceRepository invoices,
            AssetRepository assets,
            LeadRepository leads,
            RequestService requestService) {
        this.requests = requests;
        this.projects = projects;
        this.invoices = invoices;
        this.assets = assets;
        this.leads = leads;
        this.requestService = requestService;
    }

    public record PortalHome(
            long openRequests,
            long unreadReplies,
            long amountDueCents,
            List<Project> projects,
            List<ClientRequest> recentRequests,
            List<Asset> recentFiles,
            List<Invoice> openInvoices) {}

    public record AdminQueue(
            long newRequests,
            long awaitingReply,
            long overdueRequests,
            long unpaidCents,
            long openInvoices,
            long callsDue,
            long activeProjects,
            long leadsInPipeline,
            List<ClientRequest> queue,
            List<Lead> callList) {}

    @Transactional(readOnly = true)
    public PortalHome portalHome(AuthPrincipal me) {
        UUID orgId = me.orgId();
        List<ClientRequest> open = requestService.list(me, orgId, null, null, null, null, true);
        long unread = open.stream().filter(r -> requestService.hasUnread(me, r)).count();

        return new PortalHome(
                open.size(),
                unread,
                invoices.outstandingCentsForOrg(orgId),
                projects.findByOrgIdOrderByCreatedAtDesc(orgId),
                open.stream().limit(5).toList(),
                assets.findTop10ByOrgIdAndUploadStateOrderByCreatedAtDesc(orgId, UploadState.READY),
                invoices.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                        .filter(i -> i.getStatus() == InvoiceStatus.OPEN)
                        .toList());
    }

    @Transactional(readOnly = true)
    public AdminQueue adminQueue(AuthPrincipal me) {
        Instant now = Instant.now();
        List<ClientRequest> queue = requests.findByStatusInOrderByCreatedAtAsc(RequestService.OPEN_STATUSES);
        List<Lead> callList = leads.findByNextActionAtBeforeOrderByNextActionAtAsc(now);

        return new AdminQueue(
                requests.countByStatusIn(List.of(RequestStatus.NEW)),
                requests.countByStatusIn(List.of(RequestStatus.ACKNOWLEDGED, RequestStatus.IN_PROGRESS)),
                requests.countByDueAtBeforeAndStatusIn(now, RequestService.OPEN_STATUSES),
                invoices.outstandingCentsTotal(),
                invoices.countByStatus(InvoiceStatus.OPEN),
                callList.size(),
                projects.countByStatus(com.easycode.api.domain.enums.ProjectStatus.ACTIVE),
                leads.countByStatus(LeadStatus.NEW)
                        + leads.countByStatus(LeadStatus.CONTACTED)
                        + leads.countByStatus(LeadStatus.QUALIFIED)
                        + leads.countByStatus(LeadStatus.PROPOSAL),
                queue.stream().limit(25).toList(),
                callList.stream().limit(25).toList());
    }
}
