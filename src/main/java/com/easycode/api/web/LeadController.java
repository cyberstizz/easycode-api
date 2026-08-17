package com.easycode.api.web;

import com.easycode.api.domain.Lead;
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

    public LeadController(LeadService leads) {
        this.leads = leads;
    }

    /** The kanban board: every column, in ladder order. */
    @GetMapping("/board")
    public Map<String, List<LeadDtos.LeadView>> board(@RequestParam(required = false) UUID ownerId) {
        Map<LeadStatus, List<Lead>> grouped = leads.all().stream()
                .filter(l -> ownerId == null || ownerId.equals(l.getOwnerId()))
                .collect(Collectors.groupingBy(Lead::getStatus));

        Map<String, List<LeadDtos.LeadView>> board = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            board.put(
                    status.name(),
                    grouped.getOrDefault(status, List.of()).stream().map(LeadDtos.LeadView::of).toList());
        }
        return board;
    }

    /** Today's call list — anything whose next action has come due. */
    @GetMapping("/due")
    public List<LeadDtos.LeadView> due(@RequestParam(required = false) UUID ownerId) {
        return leads.dueNow(ownerId).stream().map(LeadDtos.LeadView::of).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return Map.of(
                "lead", LeadDtos.LeadView.of(leads.get(id)),
                "activities", leads.activitiesFor(id).stream().map(LeadDtos.ActivityView::of).toList());
    }

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

    /** Call disposition, logged against the lead. */
    @PostMapping("/{id}/activities")
    public LeadDtos.ActivityView logActivity(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody LeadDtos.ActivityCreate body) {

        return LeadDtos.ActivityView.of(leads.logActivity(
                me, id, body.type(), body.outcome(), body.body(), body.nextActionAt()));
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
                body.sendInvite() == null || body.sendInvite()));

        return Map.of(
                "organization", OrgDtos.OrgView.summary(result.org()),
                "contact", OrgDtos.ContactView.of(result.contact()),
                "project", ProjectDtos.ProjectView.summary(result.project(), result.org().getName()));
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
        lead.setEstValueCents(body.estValueCents());
        lead.setOfferedTier(body.offeredTier());
        lead.setLostReason(body.lostReason());
        lead.setNotes(body.notes());
    }
}
