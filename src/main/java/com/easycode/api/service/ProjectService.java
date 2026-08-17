package com.easycode.api.service;

import com.easycode.api.domain.Project;
import com.easycode.api.domain.ProjectStage;
import com.easycode.api.domain.enums.ProjectStatus;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.domain.enums.StageStatus;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.ProjectStageRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The tracker rail is the data here: six stages, always all six, in order. */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectStageRepository stages;
    private final AuditService audit;

    public ProjectService(ProjectRepository projects, ProjectStageRepository stages, AuditService audit) {
        this.projects = projects;
        this.stages = stages;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Project> forOrg(UUID orgId) {
        return projects.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<Project> recent() {
        return projects.findTop50ByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ProjectStage> stagesFor(UUID projectId) {
        return stages.findByProjectIdOrderByPositionAsc(projectId);
    }

    @Transactional
    public Project create(AuthPrincipal actor, Project draft) {
        if (draft.getStartedAt() == null) {
            draft.setStartedAt(Instant.now());
        }
        draft.setCurrentStage(StageKey.DISCOVERY);
        Project saved = projects.save(draft);

        List<ProjectStage> rail = new ArrayList<>();
        for (StageKey key : StageKey.ORDER) {
            ProjectStage stage = new ProjectStage();
            stage.setProjectId(saved.getId());
            stage.setStageKey(key);
            stage.setPosition((short) key.position());
            if (key == StageKey.DISCOVERY) {
                stage.setStatus(StageStatus.ACTIVE);
                stage.setStartedAt(Instant.now());
            }
            rail.add(stage);
        }
        stages.saveAll(rail);

        audit.record(actor, "project.create", "project", saved.getId(),
                Map.of("orgId", saved.getOrgId().toString(), "name", saved.getName()));
        return saved;
    }

    @Transactional
    public Project update(AuthPrincipal actor, Project project, Project patch) {
        if (patch.getName() != null) project.setName(patch.getName());
        if (patch.getType() != null) project.setType(patch.getType());
        if (patch.getStatus() != null) project.setStatus(patch.getStatus());
        if (patch.getContractCents() != null) project.setContractCents(patch.getContractCents());
        if (patch.getDepositCents() != null) project.setDepositCents(patch.getDepositCents());
        if (patch.getEstLaunchAt() != null) project.setEstLaunchAt(patch.getEstLaunchAt());
        if (patch.getLiveUrl() != null) project.setLiveUrl(patch.getLiveUrl());
        if (patch.getPreviewUrl() != null) project.setPreviewUrl(patch.getPreviewUrl());
        if (patch.getRepoUrl() != null) project.setRepoUrl(patch.getRepoUrl());
        if (patch.getPlanId() != null) project.setPlanId(patch.getPlanId());
        Project saved = projects.save(project);
        audit.record(actor, "project.update", "project", project.getId());
        return saved;
    }

    @Transactional
    public ProjectStage updateStage(
            AuthPrincipal actor,
            UUID projectId,
            StageKey key,
            StageStatus status,
            Short progressPct,
            String clientNote,
            String internalNote) {

        ProjectStage stage = stages.findByProjectIdAndStageKey(projectId, key)
                .orElseThrow(() -> ApiException.notFound("Stage"));

        if (status != null) {
            stage.setStatus(status);
            if (status == StageStatus.ACTIVE && stage.getStartedAt() == null) {
                stage.setStartedAt(Instant.now());
            }
            if (status == StageStatus.COMPLETE) {
                stage.setCompletedAt(Instant.now());
                stage.setProgressPct((short) 100);
            }
        }
        if (progressPct != null) {
            stage.setProgressPct((short) Math.max(0, Math.min(100, progressPct)));
        }
        if (clientNote != null) stage.setClientNote(clientNote);
        if (internalNote != null) stage.setInternalNote(internalNote);

        ProjectStage saved = stages.save(stage);

        if (status == StageStatus.ACTIVE) {
            projects.findById(projectId).ifPresent(p -> {
                p.setCurrentStage(key);
                projects.save(p);
            });
        }

        audit.record(actor, "project.stage.update", "project_stage", stage.getId(),
                Map.of("projectId", projectId.toString(), "stage", key.name()));
        return saved;
    }

    /** Completes the current stage and lights up the next one. */
    @Transactional
    public Project advance(AuthPrincipal actor, Project project) {
        StageKey current = project.getCurrentStage();
        StageKey next = current.next();
        if (next == current) {
            throw ApiException.badRequest("This project is already at the final stage");
        }

        stages.findByProjectIdAndStageKey(project.getId(), current).ifPresent(s -> {
            s.setStatus(StageStatus.COMPLETE);
            s.setProgressPct((short) 100);
            s.setCompletedAt(Instant.now());
            stages.save(s);
        });
        stages.findByProjectIdAndStageKey(project.getId(), next).ifPresent(s -> {
            s.setStatus(StageStatus.ACTIVE);
            if (s.getStartedAt() == null) {
                s.setStartedAt(Instant.now());
            }
            stages.save(s);
        });

        project.setCurrentStage(next);
        if (next == StageKey.MAINTENANCE) {
            project.setStatus(ProjectStatus.ACTIVE);
        }
        Project saved = projects.save(project);

        audit.record(actor, "project.advance", "project", project.getId(),
                Map.of("from", current.name(), "to", next.name()));
        return saved;
    }
}
