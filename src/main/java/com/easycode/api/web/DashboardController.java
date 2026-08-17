package com.easycode.api.web;

import com.easycode.api.domain.Organization;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.DashboardService;
import com.easycode.api.web.dto.AssetDtos;
import com.easycode.api.web.dto.BillingDtos;
import com.easycode.api.web.dto.ProjectDtos;
import com.easycode.api.web.dto.RequestDtos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class DashboardController {

    private final DashboardService dashboards;
    private final AccessService access;
    private final OrganizationRepository orgs;

    public DashboardController(
            DashboardService dashboards, AccessService access, OrganizationRepository orgs) {
        this.dashboards = dashboards;
        this.access = access;
        this.orgs = orgs;
    }

    /** Portal home is an action surface, not the tracker. */
    @GetMapping("/portal/home")
    public Map<String, Object> portalHome(@AuthenticationPrincipal AuthPrincipal me) {
        if (me.isStaff()) {
            throw com.easycode.api.error.ApiException.badRequest(
                    "Staff accounts use /v1/admin/dashboard");
        }
        DashboardService.PortalHome home = dashboards.portalHome(me);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openRequests", home.openRequests());
        out.put("unreadReplies", home.unreadReplies());
        out.put("amountDueCents", home.amountDueCents());
        out.put("projects", home.projects().stream()
                .map(p -> ProjectDtos.ProjectView.summary(p, orgName(p.getOrgId())))
                .toList());
        out.put("recentRequests", home.recentRequests().stream()
                .map(r -> RequestDtos.RequestView.of(
                        r, orgName(r.getOrgId()), null, null, 0, false))
                .toList());
        out.put("recentFiles", home.recentFiles().stream().map(AssetDtos.AssetView::of).toList());
        out.put("openInvoices", home.openInvoices().stream()
                .map(i -> BillingDtos.InvoiceView.of(i, false))
                .toList());
        return out;
    }

    /** Today's queue: what actually needs doing before the phones start. */
    @GetMapping("/admin/dashboard")
    public Map<String, Object> adminDashboard(@AuthenticationPrincipal AuthPrincipal me) {
        access.requireStaff(me);
        DashboardService.AdminQueue queue = dashboards.adminQueue(me);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("newRequests", queue.newRequests());
        out.put("awaitingReply", queue.awaitingReply());
        out.put("overdueRequests", queue.overdueRequests());
        out.put("unpaidCents", queue.unpaidCents());
        out.put("openInvoices", queue.openInvoices());
        out.put("callsDue", queue.callsDue());
        out.put("activeProjects", queue.activeProjects());
        out.put("leadsInPipeline", queue.leadsInPipeline());
        out.put("queue", queue.queue().stream()
                .map(r -> RequestDtos.RequestView.of(r, orgName(r.getOrgId()), null, null, 0, false))
                .toList());
        out.put("callList", queue.callList().stream()
                .map(com.easycode.api.web.dto.LeadDtos.LeadView::of)
                .toList());
        return out;
    }

    private String orgName(UUID orgId) {
        return orgId == null ? null : orgs.findById(orgId).map(Organization::getName).orElse(null);
    }
}
