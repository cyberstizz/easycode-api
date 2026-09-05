package com.easycode.api.service;

import com.easycode.api.domain.Project;
import com.easycode.api.domain.ProjectStage;
import com.easycode.api.domain.StageMessage;
import com.easycode.api.domain.StageRead;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ProjectStageRepository;
import com.easycode.api.repo.StageMessageRepository;
import com.easycode.api.repo.StageReadRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The thread under a stage update, for both sides.
 *
 * <p>Access is delegated to {@link AccessService#project}: a client can only reach stages
 * on their own org's projects; staff reach everything. There is no internal-only flag here
 * on purpose — the developer's private notes live in project_stages.internal_note, and
 * keeping the thread single-audience means nothing in it can leak by a mis-set flag.
 */
@Service
public class StageMessageService {

    private final AccessService access;
    private final ProjectStageRepository stages;
    private final StageMessageRepository messages;
    private final StageReadRepository reads;
    private final UserRepository users;
    private final AuditService audit;

    public StageMessageService(
            AccessService access,
            ProjectStageRepository stages,
            StageMessageRepository messages,
            StageReadRepository reads,
            UserRepository users,
            AuditService audit) {
        this.access = access;
        this.stages = stages;
        this.messages = messages;
        this.reads = reads;
        this.users = users;
        this.audit = audit;
    }

    public record Thread(UUID stageId, List<StageMessage> messages, Instant clientLastReadAt) {}

    private ProjectStage stage(AuthPrincipal me, UUID projectId, StageKey key) {
        Project project = access.project(me, projectId);
        return stages.findByProjectIdAndStageKey(project.getId(), key)
                .orElseThrow(() -> ApiException.notFound("Stage"));
    }

    /**
     * Reading the thread also records that you read it. For staff, the response carries
     * when the client last opened this stage — the "Latavia last read this" line.
     */
    @Transactional
    public Thread thread(AuthPrincipal me, UUID projectId, StageKey key) {
        ProjectStage stage = stage(me, projectId, key);
        markRead(me, stage.getId());

        Instant clientRead = null;
        if (me.isStaff()) {
            clientRead = reads.findByStageId(stage.getId()).stream()
                    .filter(r -> users.findById(r.getUserId())
                            .map(u -> u.getRole() == Role.CLIENT).orElse(false))
                    .map(StageRead::getReadAt)
                    .max(Instant::compareTo)
                    .orElse(null);
        }
        return new Thread(stage.getId(), messages.findByStageIdOrderByCreatedAtAsc(stage.getId()), clientRead);
    }

    @Transactional
    public StageMessage post(AuthPrincipal me, UUID projectId, StageKey key, String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("Write something first");
        }
        if (body.length() > 20_000) {
            throw ApiException.badRequest("That's too long for one reply — split it up");
        }
        ProjectStage stage = stage(me, projectId, key);

        StageMessage m = new StageMessage();
        m.setStageId(stage.getId());
        m.setProjectId(projectId);
        m.setAuthorId(me.userId());
        m.setAuthorName(Optional.ofNullable(me.name()).filter(n -> !n.isBlank()).orElse(me.email()));
        m.setAuthorRole(me.role().name());
        m.setBody(body.trim());
        StageMessage saved = messages.save(m);

        markRead(me, stage.getId());
        audit.record(me, "stage.message", "project_stage", stage.getId(),
                Map.of("stage", key.name(), "chars", String.valueOf(saved.getBody().length())));
        return saved;
    }

    private void markRead(AuthPrincipal me, UUID stageId) {
        StageRead r = reads.findById(new StageRead.Key(stageId, me.userId())).orElseGet(() -> {
            StageRead n = new StageRead();
            n.setStageId(stageId);
            n.setUserId(me.userId());
            return n;
        });
        r.setReadAt(Instant.now());
        reads.save(r);
    }
}