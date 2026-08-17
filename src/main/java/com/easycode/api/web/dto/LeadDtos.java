package com.easycode.api.web.dto;

import com.easycode.api.domain.Lead;
import com.easycode.api.domain.LeadActivity;
import com.easycode.api.domain.enums.ActivityOutcome;
import com.easycode.api.domain.enums.ActivityType;
import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
            Integer estValueCents,
            DealTier offeredTier,
            String lostReason,
            String notes) {}

    public record ActivityCreate(
            ActivityType type,
            ActivityOutcome outcome,
            String body,
            Instant nextActionAt) {}

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
            boolean overdue,
            Integer estValueCents,
            DealTier offeredTier,
            String lostReason,
            String notes,
            Instant createdAt,
            Instant updatedAt) {

        public static LeadView of(Lead l) {
            boolean overdue = l.getNextActionAt() != null && l.getNextActionAt().isBefore(Instant.now());
            return new LeadView(
                    l.getId(), l.getOrgId(), l.getBusinessName(), l.getContactName(), l.getEmail(),
                    l.getPhone(), l.getSource(), l.getStatus(), l.getOwnerId(), l.getNextActionAt(),
                    overdue, l.getEstValueCents(), l.getOfferedTier(), l.getLostReason(), l.getNotes(),
                    l.getCreatedAt(), l.getUpdatedAt());
        }
    }

    public record ActivityView(
            UUID id, ActivityType type, ActivityOutcome outcome, String body, UUID userId, Instant occurredAt) {

        public static ActivityView of(LeadActivity a) {
            return new ActivityView(
                    a.getId(), a.getType(), a.getOutcome(), a.getBody(), a.getUserId(), a.getOccurredAt());
        }
    }

    public record ContactFormInput(
            @NotBlank String name,
            @NotBlank String email,
            String phone,
            String business,
            @NotBlank String message) {}
}
