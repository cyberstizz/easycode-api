package com.easycode.api.web.dto;

import com.easycode.api.domain.ChangeOrder;
import com.easycode.api.domain.ClientRequest;
import com.easycode.api.domain.RequestMessage;
import com.easycode.api.domain.enums.BillingDisposition;
import com.easycode.api.domain.enums.ChangeOrderStatus;
import com.easycode.api.domain.enums.Priority;
import com.easycode.api.domain.enums.RequestStatus;
import com.easycode.api.domain.enums.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RequestDtos {

    private RequestDtos() {}

    public record RequestCreate(
            UUID orgId,
            UUID projectId,
            RequestType type,
            @NotBlank @Size(max = 160) String title,
            String body,
            Priority priority) {}

    public record MessageCreate(@NotBlank String body, boolean internalOnly) {}

    public record TriageUpdate(
            RequestStatus status,
            BillingDisposition billing,
            UUID assigneeId,
            Priority priority,
            Instant dueAt) {}

    public record ChangeOrderCreate(
            @Positive int amountCents,
            @NotBlank String description) {}

    public record MessageView(
            UUID id, UUID authorId, String authorName, String body, boolean internalOnly, Instant createdAt) {

        public static MessageView of(RequestMessage m, Map<UUID, String> names) {
            return new MessageView(
                    m.getId(),
                    m.getAuthorId(),
                    m.getAuthorId() == null ? "EasyCode" : names.getOrDefault(m.getAuthorId(), "EasyCode"),
                    m.getBody(),
                    m.isInternalOnly(),
                    m.getCreatedAt());
        }
    }

    public record ChangeOrderView(
            UUID id,
            int amountCents,
            String description,
            ChangeOrderStatus status,
            Instant approvedAt,
            UUID invoiceId,
            Instant createdAt) {

        public static ChangeOrderView of(ChangeOrder c) {
            return new ChangeOrderView(
                    c.getId(), c.getAmountCents(), c.getDescription(), c.getStatus(),
                    c.getApprovedAt(), c.getInvoiceId(), c.getCreatedAt());
        }
    }

    public record RequestView(
            UUID id,
            UUID orgId,
            String orgName,
            UUID projectId,
            String projectName,
            RequestType type,
            String title,
            Priority priority,
            RequestStatus status,
            BillingDisposition billing,
            UUID assigneeId,
            String assigneeName,
            Instant dueAt,
            Instant firstResponseAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            long messageCount,
            boolean unread) {

        public static RequestView of(
                ClientRequest r,
                String orgName,
                String projectName,
                String assigneeName,
                long messageCount,
                boolean unread) {
            return new RequestView(
                    r.getId(), r.getOrgId(), orgName, r.getProjectId(), projectName, r.getType(),
                    r.getTitle(), r.getPriority(), r.getStatus(), r.getBilling(), r.getAssigneeId(),
                    assigneeName, r.getDueAt(), r.getFirstResponseAt(), r.getClosedAt(),
                    r.getCreatedAt(), r.getUpdatedAt(), messageCount, unread);
        }
    }

    public record RequestDetail(
            RequestView request,
            List<MessageView> messages,
            List<AssetDtos.AssetView> attachments,
            List<ChangeOrderView> changeOrders) {}
}
