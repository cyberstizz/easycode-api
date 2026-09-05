package com.easycode.api.web;

import com.easycode.api.domain.StageMessage;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.StageMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The stage thread. Lives under /v1/projects (not /v1/admin) because both the client and
 * the developer read and write it; org scoping happens in AccessService.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/stages/{stageKey}/messages")
public class StageMessageController {

    private final StageMessageService svc;

    public StageMessageController(StageMessageService svc) {
        this.svc = svc;
    }

    public record MessageView(
            UUID id, UUID authorId, String authorName, String authorRole, String body, Instant createdAt) {
        static MessageView of(StageMessage m) {
            return new MessageView(
                    m.getId(), m.getAuthorId(), m.getAuthorName(), m.getAuthorRole(), m.getBody(), m.getCreatedAt());
        }
    }

    public record PostMessage(@NotBlank String body) {}

    @GetMapping
    public Map<String, Object> thread(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID projectId,
            @PathVariable StageKey stageKey) {
        StageMessageService.Thread t = svc.thread(me, projectId, stageKey);
        List<MessageView> items = t.messages().stream().map(MessageView::of).toList();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("stageId", t.stageId());
        out.put("items", items);
        out.put("clientLastReadAt", t.clientLastReadAt());
        return out;
    }

    @PostMapping
    public MessageView post(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID projectId,
            @PathVariable StageKey stageKey,
            @Valid @RequestBody PostMessage body) {
        return MessageView.of(svc.post(me, projectId, stageKey, body.body()));
    }
}