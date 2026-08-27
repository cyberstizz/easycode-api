package com.easycode.api.service;

import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Lead;
import com.easycode.api.domain.LeadActivity;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Project;
import com.easycode.api.domain.enums.ActivityOutcome;
import com.easycode.api.domain.enums.ActivityType;
import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.LeadStatus;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.LeadActivityRepository;
import com.easycode.api.repo.LeadRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.web.dto.LeadDtos;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** For the phone work: six days a week, and eventually more than one person doing it. */
@Service
public class LeadService {

    private final LeadRepository leads;
    private final LeadActivityRepository activities;
    private final OrgService orgService;
    private final ProjectService projectService;
    private final AuditService audit;

    public LeadService(
            LeadRepository leads,
            LeadActivityRepository activities,
            OrgService orgService,
            ProjectService projectService,
            AuditService audit) {
        this.leads = leads;
        this.activities = activities;
        this.orgService = orgService;
        this.projectService = projectService;
        this.audit = audit;
    }

    public record ConvertRequest(
            String orgName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String projectName,
            String projectType,
            DealTier dealTier,
            Integer contractCents,
            Integer depositCents,
            Instant startedAt,
            Instant estLaunchAt,
            boolean sendInvite) {}

    /**
     * The result of a conversion.
     *
     * <p>{@code invite} carries the issued invite when one was requested. Without it
     * the raw token is created and immediately discarded, so the only copy of the
     * accept link is the emailed one — which is useless while email is switched off.
     */
    public record Converted(
            Organization org, Contact contact, Project project, OrgService.IssuedInvite invite) {}

    @Transactional(readOnly = true)
    public List<Lead> all() {
        return leads.findAll();
    }

    @Transactional(readOnly = true)
    public List<Lead> byStatus(LeadStatus status) {
        return leads.findByStatusOrderByUpdatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Lead> dueNow(UUID ownerId) {
        Instant now = Instant.now();
        return ownerId == null
                ? leads.findByNextActionAtBeforeOrderByNextActionAtAsc(now)
                : leads.findByOwnerIdAndNextActionAtBeforeOrderByNextActionAtAsc(ownerId, now);
    }

    @Transactional(readOnly = true)
    public Lead get(UUID id) {
        return leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead"));
    }

    @Transactional(readOnly = true)
    public List<LeadActivity> activitiesFor(UUID leadId) {
        return activities.findByLeadIdOrderByOccurredAtDesc(leadId);
    }

    /** Call counts for a lead, used by the detail view. */
    @Transactional(readOnly = true)
    public int[] callCounts(UUID leadId) {
        return new int[] {
            (int) activities.countByLeadId(leadId),
            (int) activities.countByLeadIdAndOutcome(leadId, ActivityOutcome.CONNECTED),
        };
    }

    @Transactional
    public Lead create(AuthPrincipal actor, Lead draft) {
        if (draft.getOwnerId() == null) {
            draft.setOwnerId(actor.userId());
        }
        Lead saved = leads.save(draft);
        audit.record(actor, "lead.create", "lead", saved.getId(), Map.of("name", saved.getBusinessName()));
        return saved;
    }

    @Transactional
    public Lead update(AuthPrincipal actor, UUID id, Lead patch) {
        Lead lead = get(id);
        if (patch.getBusinessName() != null) lead.setBusinessName(patch.getBusinessName());
        if (patch.getContactName() != null) lead.setContactName(patch.getContactName());
        if (patch.getEmail() != null) lead.setEmail(patch.getEmail());
        if (patch.getPhone() != null) lead.setPhone(patch.getPhone());
        if (patch.getSource() != null) lead.setSource(patch.getSource());
        if (patch.getStatus() != null) lead.setStatus(patch.getStatus());
        if (patch.getOwnerId() != null) lead.setOwnerId(patch.getOwnerId());
        if (patch.getNextActionAt() != null) lead.setNextActionAt(patch.getNextActionAt());
        if (patch.getNextActionNote() != null) lead.setNextActionNote(patch.getNextActionNote());
        if (patch.getEstValueCents() != null) lead.setEstValueCents(patch.getEstValueCents());
        if (patch.getOfferedTier() != null) lead.setOfferedTier(patch.getOfferedTier());
        if (patch.getLostReason() != null) lead.setLostReason(patch.getLostReason());
        if (patch.getNotes() != null) lead.setNotes(patch.getNotes());
        Lead saved = leads.save(lead);
        audit.record(actor, "lead.update", "lead", id, Map.of("status", saved.getStatus().name()));
        return saved;
    }

    /**
     * Call disposition.
     *
     * <p>Logging one always moves the next-action date, so nothing goes cold. The
     * log-a-call screen won't save without a next action; this keeps the same
     * guarantee for any other caller.
     */
    @Transactional
    public LeadActivity logActivity(
            AuthPrincipal actor,
            UUID leadId,
            ActivityType type,
            ActivityOutcome outcome,
            String body,
            Integer durationSeconds,
            List<String> objectionTags,
            DealTier rungOffered,
            Instant nextActionAt,
            String nextActionNote,
            LeadStatus status) {

        Lead lead = get(leadId);

        LeadActivity activity = new LeadActivity();
        activity.setLeadId(leadId);
        activity.setUserId(actor.userId());
        activity.setType(type == null ? ActivityType.CALL : type);
        activity.setOutcome(outcome);
        activity.setBody(body);
        activity.setDurationSeconds(durationSeconds);
        activity.setObjectionTags(
                objectionTags == null ? new String[0] : objectionTags.toArray(String[]::new));
        activity.setRungOffered(rungOffered);
        LeadActivity saved = activities.save(activity);

        if (nextActionAt != null) {
            lead.setNextActionAt(nextActionAt);
        }
        if (nextActionNote != null) {
            lead.setNextActionNote(nextActionNote);
        }

        // The latest rung on the table becomes the lead's current offer. The
        // per-call history stays on the activity, which is what shows whether
        // we're dropping to the floor too early.
        if (rungOffered != null) {
            lead.setOfferedTier(rungOffered);
        }

        // An explicit status from the caller wins. The inferences below only run
        // for callers that don't set one.
        if (status != null) {
            lead.setStatus(status);
        } else {
            if (lead.getStatus() == LeadStatus.NEW && outcome == ActivityOutcome.CONNECTED) {
                lead.setStatus(LeadStatus.CONTACTED);
            }
            if (rungOffered != null && lead.getStatus() == LeadStatus.CONTACTED) {
                lead.setStatus(LeadStatus.PITCHED);
            }
            if (outcome == ActivityOutcome.NOT_INTERESTED) {
                lead.setStatus(LeadStatus.LOST);
                lead.setLostReason(body);
            }
        }

        leads.save(lead);
        audit.record(actor, "lead.activity", "lead", leadId,
                Map.of("outcome", outcome == null ? "NONE" : outcome.name(),
                       "status", lead.getStatus().name()));
        return saved;
    }

    /**
     * The numbers under the pipeline board.
     *
     * <p>Every one of these is only computable because calls are logged with
     * structure rather than free text. That's the argument for the discipline,
     * made visible in the UI.
     */
    @Transactional(readOnly = true)
    public LeadDtos.PipelineStats pipelineStats(UUID ownerId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant monthStart = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Lead> all = leads.findAll().stream()
                .filter(l -> ownerId == null || ownerId.equals(l.getOwnerId()))
                .toList();

        long closedThisMonth = all.stream()
                .filter(l -> l.getStatus() == LeadStatus.WON)
                .filter(l -> l.getUpdatedAt() != null && l.getUpdatedAt().isAfter(monthStart))
                .count();

        // Two-year value of every live lead, at the rung currently on the table.
        // Nothing offered yet is valued at the preferred deal, since that's what
        // we lead with.
        long pipelineValue = all.stream()
                .filter(l -> l.getStatus() != LeadStatus.WON && l.getStatus() != LeadStatus.LOST)
                .mapToLong(l -> twoYearValueCents(l.getOfferedTier()))
                .sum();

        List<LeadDtos.ObjectionCount> objections = new ArrayList<>();
        for (Object[] row : activities.objectionCountsForLostLeads()) {
            objections.add(new LeadDtos.ObjectionCount((String) row[0], ((Number) row[1]).longValue()));
        }

        List<LeadDtos.RungConversion> rungs = new ArrayList<>();
        for (Object[] row : activities.rungConversion()) {
            rungs.add(new LeadDtos.RungConversion(
                    DealTier.valueOf((String) row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()));
        }

        long wonAllTime = all.stream().filter(l -> l.getStatus() == LeadStatus.WON).count();
        long dialsAllTime = activities.dialsSince(Instant.EPOCH, ownerId);
        Integer dialsPerClose = wonAllTime == 0 ? null : (int) (dialsAllTime / wonAllTime);

        return new LeadDtos.PipelineStats(
                (int) activities.dialsSince(startOfDay, ownerId),
                60,
                (int) activities.dialsSince(weekAgo, ownerId),
                (int) closedThisMonth,
                pipelineValue,
                objections,
                rungs,
                dialsPerClose);
    }

    /** The ladder's economics. Preferred is worth more than Standard — that's the point of it. */
    private long twoYearValueCents(DealTier tier) {
        if (tier == null) return 140_000L; // assume preferred, what we lead with
        return switch (tier) {
            case STANDARD -> 120_000L; // $1,200, no contract
            case PREFERRED -> 140_000L; // $200 + $50 x 24
            case FLOOR -> 130_000L; // $100 + $50 x 24
            case SPECIAL -> 0L; // comped
        };
    }

    /** Lead → client in one move: org, contact, project with the six-stage rail, and the portal invite. */
    @Transactional
    public Converted convert(AuthPrincipal actor, UUID leadId, ConvertRequest req) {
        Lead lead = get(leadId);
        if (lead.getOrgId() != null) {
            throw ApiException.badRequest("That lead has already been converted");
        }

        Organization draft = new Organization();
        draft.setName(req.orgName() != null ? req.orgName() : lead.getBusinessName());
        draft.setPhone(lead.getPhone());
        draft.setNotes(lead.getNotes());
        draft.setDealTier(req.dealTier() != null ? req.dealTier() : DealTier.STANDARD);
        Organization org = orgService.create(actor, draft);

        Contact contactDraft = new Contact();
        contactDraft.setName(req.contactName() != null ? req.contactName() : lead.getContactName());
        contactDraft.setEmail(req.contactEmail() != null ? req.contactEmail() : lead.getEmail());
        contactDraft.setPhone(req.contactPhone() != null ? req.contactPhone() : lead.getPhone());
        contactDraft.setPrimaryContact(true);
        if (contactDraft.getEmail() == null || contactDraft.getEmail().isBlank()) {
            throw ApiException.badRequest("A contact email is required to convert a lead");
        }
        if (contactDraft.getName() == null || contactDraft.getName().isBlank()) {
            contactDraft.setName(contactDraft.getEmail());
        }
        Contact contact = orgService.addContact(actor, org.getId(), contactDraft);

        Project projectDraft = new Project();
        projectDraft.setOrgId(org.getId());
        projectDraft.setName(req.projectName() != null ? req.projectName() : org.getName() + " website");
        projectDraft.setType(req.projectType());
        projectDraft.setContractCents(req.contractCents());
        projectDraft.setDepositCents(req.depositCents());
        // Kickoff and launch come off the convert screen. The launch date is the
        // first thing the client sees on their tracker, so it matters that it's
        // the one you actually agreed to on the phone.
        projectDraft.setStartedAt(req.startedAt() != null ? req.startedAt() : Instant.now());
        projectDraft.setEstLaunchAt(req.estLaunchAt());
        Project project = projectService.create(actor, projectDraft);

        OrgService.IssuedInvite invite =
                req.sendInvite() ? orgService.invite(actor, contact.getId()) : null;

        lead.setOrgId(org.getId());
        lead.setStatus(LeadStatus.WON);
        lead.setNextActionAt(null);
        leads.save(lead);

        audit.record(actor, "lead.convert", "lead", leadId,
                Map.of("orgId", org.getId().toString(), "projectId", project.getId().toString()));
        return new Converted(org, contact, project, invite);
    }
}