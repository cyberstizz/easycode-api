package com.easycode.api.web.dto;

import com.easycode.api.domain.Project;
import com.easycode.api.domain.ProjectStage;
import com.easycode.api.domain.enums.ProjectStatus;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.domain.enums.StageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {}

    public record ProjectCreate(
            @NotNull UUID orgId,
            @NotBlank String name,
            String type,
            Integer contractCents,
            Integer depositCents,
            Instant estLaunchAt,
            UUID planId) {}

    public record ProjectUpdate(
            String name,
            String type,
            ProjectStatus status,
            Integer contractCents,
            Integer depositCents,
            Instant estLaunchAt,
            String liveUrl,
            String previewUrl,
            String repoUrl,
            UUID planId) {}

    public record StageUpdate(
            StageStatus status,
            Short progressPct,
            String clientNote,
            String internalNote) {}

    /** One node on the tracker rail. */
    public record StageView(
            UUID id,
            StageKey key,
            String label,
            int position,
            StageStatus status,
            int progressPct,
            Instant startedAt,
            Instant completedAt,
            String clientNote,
            String internalNote) {

        public static StageView of(ProjectStage s, boolean includeInternal) {
            return new StageView(
                    s.getId(),
                    s.getStageKey(),
                    s.getStageKey().label(),
                    s.getPosition(),
                    s.getStatus(),
                    s.getProgressPct(),
                    s.getStartedAt(),
                    s.getCompletedAt(),
                    s.getClientNote(),
                    includeInternal ? s.getInternalNote() : null);
        }
    }

    public record ProjectView(
            UUID id,
            UUID orgId,
            String orgName,
            String name,
            String type,
            ProjectStatus status,
            StageKey currentStage,
            int currentStagePosition,
            Integer contractCents,
            Integer depositCents,
            Instant startedAt,
            Instant estLaunchAt,
            String liveUrl,
            String previewUrl,
            String repoUrl,
            List<StageView> stages) {

        public static ProjectView of(
                Project p, String orgName, List<ProjectStage> stages, boolean includeInternal) {
            return new ProjectView(
                    p.getId(), p.getOrgId(), orgName, p.getName(), p.getType(), p.getStatus(),
                    p.getCurrentStage(), p.getCurrentStage().position(), p.getContractCents(),
                    p.getDepositCents(), p.getStartedAt(), p.getEstLaunchAt(), p.getLiveUrl(),
                    p.getPreviewUrl(), p.getRepoUrl(),
                    stages == null ? List.of() : stages.stream().map(s -> StageView.of(s, includeInternal)).toList());
        }

        public static ProjectView summary(Project p, String orgName) {
            return of(p, orgName, List.of(), false);
        }
    }
}
