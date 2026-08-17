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
import java.time.Instant;
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
            boolean sendInvite) {}

    public record Converted(Organization org, Contact contact, Project project) {}

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
        if (patch.getEstValueCents() != null) lead.setEstValueCents(patch.getEstValueCents());
        if (patch.getOfferedTier() != null) lead.setOfferedTier(patch.getOfferedTier());
        if (patch.getLostReason() != null) lead.setLostReason(patch.getLostReason());
        if (patch.getNotes() != null) lead.setNotes(patch.getNotes());
        Lead saved = leads.save(lead);
        audit.record(actor, "lead.update", "lead", id, Map.of("status", saved.getStatus().name()));
        return saved;
    }

    /** Call disposition. Logging one always moves the next-action date so nothing goes cold. */
    @Transactional
    public LeadActivity logActivity(
            AuthPrincipal actor,
            UUID leadId,
            ActivityType type,
            ActivityOutcome outcome,
            String body,
            Instant nextActionAt) {

        Lead lead = get(leadId);

        LeadActivity activity = new LeadActivity();
        activity.setLeadId(leadId);
        activity.setUserId(actor.userId());
        activity.setType(type == null ? ActivityType.CALL : type);
        activity.setOutcome(outcome);
        activity.setBody(body);
        LeadActivity saved = activities.save(activity);

        if (nextActionAt != null) {
            lead.setNextActionAt(nextActionAt);
        }
        if (lead.getStatus() == LeadStatus.NEW && outcome == ActivityOutcome.CONNECTED) {
            lead.setStatus(LeadStatus.CONTACTED);
        }
        if (outcome == ActivityOutcome.NOT_INTERESTED) {
            lead.setStatus(LeadStatus.LOST);
            lead.setLostReason(body);
        }
        leads.save(lead);
        return saved;
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
        Project project = projectService.create(actor, projectDraft);

        if (req.sendInvite()) {
            orgService.invite(actor, contact.getId());
        }

        lead.setOrgId(org.getId());
        lead.setStatus(LeadStatus.WON);
        lead.setNextActionAt(null);
        leads.save(lead);

        audit.record(actor, "lead.convert", "lead", leadId,
                Map.of("orgId", org.getId().toString(), "projectId", project.getId().toString()));
        return new Converted(org, contact, project);
    }
}
