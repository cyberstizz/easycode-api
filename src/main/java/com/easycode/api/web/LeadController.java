package com.easycode.api.web;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Lead;
import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.LeadStatus;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.LeadService;
import com.easycode.api.web.dto.LeadDtos;
import com.easycode.api.web.dto.OrgDtos;
import com.easycode.api.web.dto.ProjectDtos;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/leads")
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class LeadController {

    private final LeadService leads;
    private final AppProperties props;

    public LeadController(LeadService leads, AppProperties props) {
        this.leads = leads;
        this.props = props;
    }

    /**
     * The kanban board.
     *
     * <p>Returns {@code {columns: {STATUS: [...]}, stats: {...}}} rather than a
     * bare map. The envelope leaves room for the pipeline's insight cards without
     * a second round trip, and it means every list response in the API has a
     * predictable outer shape.
     */
    @GetMapping("/board")
    public Map<String, Object> board(@RequestParam(required = false) UUID ownerId) {
        Map<LeadStatus, List<Lead>> grouped = leads.all().stream()
                .filter(l -> ownerId == null || ownerId.equals(l.getOwnerId()))
                .collect(Collectors.groupingBy(Lead::getStatus));

        Map<String, List<LeadDtos.LeadView>> columns = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            columns.put(
                    status.name(),
                    grouped.getOrDefault(status, List.of()).stream().map(LeadDtos.LeadView::of).toList());
        }

        return Map.of("columns", columns, "stats", leads.pipelineStats(ownerId));
    }

    /** Today's call list — anything whose next action has come due. */
    @GetMapping("/due")
    public Map<String, Object> due(@RequestParam(required = false) UUID ownerId) {
        return Map.of(
                "items", leads.dueNow(ownerId).stream().map(LeadDtos.LeadView::of).toList(),
                "stats", leads.pipelineStats(ownerId));
    }

    /** Flat list, mostly for search and pickers. */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) UUID ownerId) {
        List<LeadDtos.LeadView> items = leads.all().stream()
                .filter(l -> ownerId == null || ownerId.equals(l.getOwnerId()))
                .map(LeadDtos.LeadView::of)
                .toList();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        int[] counts = leads.callCounts(id);
        return Map.of(
                "lead", LeadDtos.LeadView.of(leads.get(id), counts[0], counts[1]),
                "activities", leads.activitiesFor(id).stream().map(LeadDtos.ActivityView::of).toList());
    }

    /** Returns the created lead directly, so the caller can navigate to it. */
    @PostMapping
    public LeadDtos.LeadView create(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody LeadDtos.LeadUpsert body) {
        Lead draft = new Lead();
        apply(draft, body);
        return LeadDtos.LeadView.of(leads.create(me, draft));
    }

    @PatchMapping("/{id}")
    public LeadDtos.LeadView update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody LeadDtos.LeadUpsert body) {
        Lead patch = new Lead();
        apply(patch, body);
        return LeadDtos.LeadView.of(leads.update(me, id, patch));
    }

    /**
     * Call disposition, logged against the lead.
     *
     * <p>Carries the objection tags, connect time, and rung offered — the fields
     * the pipeline's insight cards are built from.
     */
    @PostMapping("/{id}/activities")
    public LeadDtos.ActivityView logActivity(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody LeadDtos.ActivityCreate body) {

        return LeadDtos.ActivityView.of(leads.logActivity(
                me, id,
                body.type(), body.outcome(), body.body(),
                body.durationSeconds(), body.objectionTags(), body.rungOffered(),
                body.nextActionAt(), body.nextActionNote(), body.status()));
    }

    /** Creates the org, the contact, the project with its six stages, and sends the portal invite. */
    @PostMapping("/{id}/convert")
    public Map<String, Object> convert(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody LeadDtos.ConvertInput body) {

        LeadService.Converted result = leads.convert(me, id, new LeadService.ConvertRequest(
                body.orgName(), body.contactName(), body.contactEmail(), body.contactPhone(),
                body.projectName(), body.projectType(), body.dealTier(),
                body.contractCents(), body.depositCents(),
                body.startedAt(), body.estLaunchAt(),
                body.sendInvite() == null || body.sendInvite()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("organization", OrgDtos.OrgView.summary(result.org()));
        out.put("contact", OrgDtos.ContactView.of(result.contact()));
        out.put("project", ProjectDtos.ProjectView.summary(result.project(), result.org().getName()));

        // Same rule as the standalone invite endpoint: hand back the accept link
        // while email is off, so the person converting can actually deliver it.
        if (result.invite() != null) {
            out.put("inviteEmail", result.invite().invite().getEmail());
            out.put("inviteExpiresAt", result.invite().invite().getExpiresAt());
            out.put("emailSent", props.getResend().isEnabled());
            if (!props.getResend().isEnabled()) {
                out.put("acceptUrl",
                        props.getBaseUrl() + "/accept-invite?token=" + result.invite().rawToken());
            }
        }
        return out;
    }

    private void apply(Lead lead, LeadDtos.LeadUpsert body) {
        lead.setBusinessName(body.businessName());
        lead.setContactName(body.contactName());
        lead.setEmail(body.email());
        lead.setPhone(body.phone());
        lead.setSource(body.source());
        if (body.status() != null) {
            lead.setStatus(body.status());
        }
        lead.setOwnerId(body.ownerId());
        lead.setNextActionAt(body.nextActionAt());
        lead.setNextActionNote(body.nextActionNote());
        lead.setEstValueCents(body.estValueCents());
        lead.setOfferedTier(body.offeredTier());
        lead.setLostReason(body.lostReason());
        lead.setNotes(body.notes());
    }
}