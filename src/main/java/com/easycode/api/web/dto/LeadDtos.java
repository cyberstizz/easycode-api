package com.easycode.api.web.dto;

import com.easycode.api.domain.Lead;
import com.easycode.api.domain.LeadActivity;
import com.easycode.api.domain.enums.ActivityOutcome;
import com.easycode.api.domain.enums.ActivityType;
import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LeadDtos {

    private LeadDtos() {}

    public record LeadUpsert(
            @NotBlank String businessName,
            String contactName,
            String email,
            String phone,
            String source,
            LeadStatus status,
            UUID ownerId,
            Instant nextActionAt,
            String nextActionNote,
            Integer estValueCents,
            DealTier offeredTier,
            String lostReason,
            String notes) {}

    /** What the log-a-call screen posts. Everything but {@code type} is optional. */
    public record ActivityCreate(
            ActivityType type,
            ActivityOutcome outcome,
            String body,
            Integer durationSeconds,
            List<String> objectionTags,
            DealTier rungOffered,
            Instant nextActionAt,
            String nextActionNote,
            LeadStatus status) {}

    public record ConvertInput(
            String orgName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String projectName,
            String projectType,
            DealTier dealTier,
            Integer contractCents,
            Integer depositCents,
            /** Kickoff. Defaults to now if the caller doesn't set one. */
            Instant startedAt,
            /** The date the client sees on their tracker from day one. */
            Instant estLaunchAt,
            Boolean sendInvite) {}

    public record LeadView(
            UUID id,
            UUID orgId,
            String businessName,
            String contactName,
            String email,
            String phone,
            String source,
            LeadStatus status,
            UUID ownerId,
            Instant nextActionAt,
            String nextActionNote,
            boolean overdue,
            Integer estValueCents,
            DealTier offeredTier,
            String lostReason,
            String notes,
            int callCount,
            int connectedCount,
            Instant createdAt,
            Instant updatedAt) {

        /** Without counts — cheap, for board and list responses. */
        public static LeadView of(Lead l) {
            return of(l, 0, 0);
        }

        public static LeadView of(Lead l, int callCount, int connectedCount) {
            boolean overdue = l.getNextActionAt() != null && l.getNextActionAt().isBefore(Instant.now());
            return new LeadView(
                    l.getId(), l.getOrgId(), l.getBusinessName(), l.getContactName(), l.getEmail(),
                    l.getPhone(), l.getSource(), l.getStatus(), l.getOwnerId(), l.getNextActionAt(),
                    l.getNextActionNote(), overdue, l.getEstValueCents(), l.getOfferedTier(),
                    l.getLostReason(), l.getNotes(), callCount, connectedCount,
                    l.getCreatedAt(), l.getUpdatedAt());
        }
    }

    public record ActivityView(
            UUID id,
            ActivityType type,
            ActivityOutcome outcome,
            String body,
            Integer durationSeconds,
            List<String> objectionTags,
            DealTier rungOffered,
            UUID userId,
            String userName,
            Instant occurredAt) {

        public static ActivityView of(LeadActivity a) {
            return of(a, null);
        }

        public static ActivityView of(LeadActivity a, String userName) {
            return new ActivityView(
                    a.getId(), a.getType(), a.getOutcome(), a.getBody(),
                    a.getDurationSeconds(),
                    a.getObjectionTags() == null ? List.of() : List.of(a.getObjectionTags()),
                    a.getRungOffered(), a.getUserId(), userName, a.getOccurredAt());
        }
    }

    /**
     * The three numbers the pipeline shows under the board. These exist only
     * because calls get logged with structure — that's the argument for the
     * discipline, made visible.
     */
    public record PipelineStats(
            int dialsToday,
            int dialsGoal,
            int dialsThisWeek,
            int closedThisMonth,
            long pipelineValueCents,
            List<ObjectionCount> objectionCounts,
            List<RungConversion> rungConversion,
            Integer dialsPerClose) {}

    public record ObjectionCount(String tag, long losses) {}

    public record RungConversion(DealTier rung, long wins, long of) {}

    public record ContactFormInput(
            @NotBlank String name,
            @NotBlank String email,
            String phone,
            String business,
            @NotBlank String message) {}
}