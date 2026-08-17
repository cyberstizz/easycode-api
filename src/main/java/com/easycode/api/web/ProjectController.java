package com.easycode.api.web;

import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Project;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.ProjectService;
import com.easycode.api.web.dto.ProjectDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class ProjectController {

    private final ProjectService projects;
    private final AccessService access;
    private final OrganizationRepository orgs;

    public ProjectController(
            ProjectService projects, AccessService access, OrganizationRepository orgs) {
        this.projects = projects;
        this.access = access;
        this.orgs = orgs;
    }

    @GetMapping("/projects")
    public List<ProjectDtos.ProjectView> list(
            @AuthenticationPrincipal AuthPrincipal me, @RequestParam(required = false) UUID orgId) {

        List<Project> found;
        if (me.isStaff()) {
            found = orgId == null ? projects.recent() : projects.forOrg(orgId);
        } else {
            found = projects.forOrg(me.orgId());
        }
        return found.stream().map(p -> ProjectDtos.ProjectView.summary(p, orgName(p.getOrgId()))).toList();
    }

    @GetMapping("/projects/{id}")
    public ProjectDtos.ProjectView get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        Project project = access.project(me, id);
        return ProjectDtos.ProjectView.of(
                project, orgName(project.getOrgId()), projects.stagesFor(id), me.isStaff());
    }

    @PostMapping("/admin/projects")
    public ProjectDtos.ProjectView create(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ProjectDtos.ProjectCreate body) {

        access.requireStaff(me);
        Project draft = new Project();
        draft.setOrgId(body.orgId());
        draft.setName(body.name());
        draft.setType(body.type());
        draft.setContractCents(body.contractCents());
        draft.setDepositCents(body.depositCents());
        draft.setEstLaunchAt(body.estLaunchAt());
        draft.setPlanId(body.planId());
        Project saved = projects.create(me, draft);
        return ProjectDtos.ProjectView.of(
                saved, orgName(saved.getOrgId()), projects.stagesFor(saved.getId()), true);
    }

    @PatchMapping("/admin/projects/{id}")
    public ProjectDtos.ProjectView update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody ProjectDtos.ProjectUpdate body) {

        access.requireStaff(me);
        Project project = access.project(me, id);
        Project patch = new Project();
        patch.setName(body.name());
        patch.setType(body.type());
        patch.setStatus(body.status());
        patch.setContractCents(body.contractCents());
        patch.setDepositCents(body.depositCents());
        patch.setEstLaunchAt(body.estLaunchAt());
        patch.setLiveUrl(body.liveUrl());
        patch.setPreviewUrl(body.previewUrl());
        patch.setRepoUrl(body.repoUrl());
        patch.setPlanId(body.planId());
        Project saved = projects.update(me, project, patch);
        return ProjectDtos.ProjectView.of(saved, orgName(saved.getOrgId()), projects.stagesFor(id), true);
    }

    /** Move one node on the tracker without jumping the whole rail. */
    @PatchMapping("/admin/projects/{id}/stages/{stageKey}")
    public ProjectDtos.StageView updateStage(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @PathVariable StageKey stageKey,
            @RequestBody ProjectDtos.StageUpdate body) {

        access.requireStaff(me);
        access.project(me, id);
        return ProjectDtos.StageView.of(
                projects.updateStage(
                        me, id, stageKey, body.status(), body.progressPct(),
                        body.clientNote(), body.internalNote()),
                true);
    }

    /** The button that makes the client's tracker light up the next node. */
    @PostMapping("/admin/projects/{id}/advance")
    public ProjectDtos.ProjectView advance(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {

        access.requireStaff(me);
        Project saved = projects.advance(me, access.project(me, id));
        return ProjectDtos.ProjectView.of(saved, orgName(saved.getOrgId()), projects.stagesFor(id), true);
    }

    private String orgName(UUID orgId) {
        return orgs.findById(orgId).map(Organization::getName).orElse(null);
    }
}
