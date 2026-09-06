package com.easycode.api.service;

import com.easycode.api.domain.ChecklistTemplate;
import com.easycode.api.domain.ChecklistTemplateItem;
import com.easycode.api.domain.Project;
import com.easycode.api.domain.ProjectChecklistItem;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ChecklistTemplateItemRepository;
import com.easycode.api.repo.ChecklistTemplateRepository;
import com.easycode.api.repo.ProjectChecklistItemRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The internal delivery checklist for a project.
 *
 * <p>Staff only, top to bottom. Ticking an item does not touch the project's stage,
 * its percentage, or anything a client can see — that separation is the whole point.
 * The tracker stays a judgement you make; this is the record of what has actually
 * been done.
 */
@Service
public class ChecklistService {

    private final AccessService access;
    private final ChecklistTemplateRepository templates;
    private final ChecklistTemplateItemRepository templateItems;
    private final ProjectChecklistItemRepository items;
    private final AuditService audit;

    public ChecklistService(
            AccessService access,
            ChecklistTemplateRepository templates,
            ChecklistTemplateItemRepository templateItems,
            ProjectChecklistItemRepository items,
            AuditService audit) {
        this.access = access;
        this.templates = templates;
        this.templateItems = templateItems;
        this.items = items;
        this.audit = audit;
    }

    public record TemplateOption(String projectType, String label, int version, int itemCount) {}

    @Transactional(readOnly = true)
    public List<TemplateOption> options() {
        List<TemplateOption> out = new ArrayList<>();
        for (ChecklistTemplate t : templates.findAllByOrderByProjectTypeAscVersionDesc()) {
            boolean alreadyListed = out.stream().anyMatch(o -> o.projectType().equals(t.getProjectType()));
            if (!alreadyListed) {
                out.add(new TemplateOption(
                        t.getProjectType(),
                        t.getLabel(),
                        t.getVersion(),
                        templateItems.findByTemplateIdOrderByStageKeyAscPositionAsc(t.getId()).size()));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ProjectChecklistItem> forProject(AuthPrincipal me, UUID projectId) {
        access.requireStaff(me);
        Project project = access.project(me, projectId);
        return items.findByProjectIdOrderByStageKeyAscPositionAsc(project.getId());
    }

    /**
     * Copies the newest version of a template onto a project.
     *
     * @param replace true wipes any existing items first. Guarded in the UI behind a
     *     confirmation, because it discards ticks and notes.
     */
    @Transactional
    public List<ProjectChecklistItem> apply(
            AuthPrincipal me, UUID projectId, String projectType, boolean replace) {

        access.requireStaff(me);
        Project project = access.project(me, projectId);

        long existing = items.countByProjectId(project.getId());
        if (existing > 0 && !replace) {
            throw ApiException.conflict("This project already has a checklist");
        }
        if (existing > 0) {
            items.deleteByProjectId(project.getId());
        }

        ChecklistTemplate tpl = templates
                .findFirstByProjectTypeOrderByVersionDesc(projectType)
                .orElseThrow(() -> ApiException.notFound("Checklist template"));

        List<ProjectChecklistItem> batch = new ArrayList<>();
        for (ChecklistTemplateItem src : templateItems.findByTemplateIdOrderByStageKeyAscPositionAsc(tpl.getId())) {
            ProjectChecklistItem row = new ProjectChecklistItem();
            row.setProjectId(project.getId());
            row.setTemplateId(tpl.getId());
            row.setStageKey(src.getStageKey());
            row.setPosition(src.getPosition());
            // Copied, not joined — see ProjectChecklistItem's class comment.
            row.setTitle(src.getTitle());
            row.setGuidance(src.getGuidance());
            row.setEmphasis(src.isEmphasis());
            batch.add(row);
        }
        List<ProjectChecklistItem> saved = items.saveAll(batch);

        audit.record(me, "checklist.apply", "project", project.getId(),
                Map.of("type", projectType,
                        "version", String.valueOf(tpl.getVersion()),
                        "items", String.valueOf(saved.size()),
                        "replaced", String.valueOf(existing > 0)));
        return saved;
    }

    /** Tick, untick, or annotate one line. Null means "leave this field alone". */
    @Transactional
    public ProjectChecklistItem update(AuthPrincipal me, UUID itemId, Boolean done, String note) {
        access.requireStaff(me);
        ProjectChecklistItem item = items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Checklist item"));
        access.project(me, item.getProjectId());

        if (done != null && done != item.isDone()) {
            item.setDone(done);
            item.setDoneAt(done ? Instant.now() : null);
            item.setDoneBy(done ? me.userId() : null);
            item.setDoneByName(done
                    ? Optional.ofNullable(me.name()).filter(n -> !n.isBlank()).orElse(me.email())
                    : null);
        }
        if (note != null) {
            item.setNote(note.isBlank() ? null : note.trim());
        }
        return items.save(item);
    }
}