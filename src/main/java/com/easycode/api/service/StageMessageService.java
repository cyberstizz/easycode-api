package com.easycode.api.service;

import com.easycode.api.domain.Project;
import com.easycode.api.domain.ProjectStage;
import com.easycode.api.domain.StageMessage;
import com.easycode.api.domain.StageRead;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.domain.enums.StageKey;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ProjectStageRepository;
import com.easycode.api.config.AppProperties;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.repo.ContactRepository;
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
            AuditService audit,
            ContactRepository contacts,
            ProjectRepository projects,
            EmailService email,
            AppProperties props) {
        this.access = access;
        this.stages = stages;
        this.messages = messages;
        this.reads = reads;
        this.users = users;
        this.audit = audit;
        this.contacts = contacts;
        this.projects = projects;
        this.email = email;
        this.props = props;
    }

    private final ContactRepository contacts;
    private final ProjectRepository projects;
    private final EmailService email;
    private final AppProperties props;

    public record Thread(UUID stageId, List<StageMessage> messages, Instant clientLastReadAt) {}

    private Project project(AuthPrincipal me, UUID projectId) {
        return access.project(me, projectId);
    }

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

        // Tell the other side. Staff wrote it → every contact with a login on that org.
        // Client wrote it → every admin and agent. The author never emails themselves.
        String projectName = projects.findById(projectId).map(Project::getName).orElse("your project");
        String excerpt = ProjectService.excerpt(saved.getBody());
        if (me.isStaff()) {
            String link = props.getBaseUrl() + "/portal/project";
            contacts.findByOrgId(project(me, projectId).getOrgId()).stream()
                    .filter(c -> c.getUserId() != null && c.getEmail() != null && !c.getUserId().equals(me.userId()))
                    .forEach(c -> email.sendStageReply(c.getEmail(), saved.getAuthorName(), projectName, key.label(), excerpt, link));
        } else {
            String link = props.getBaseUrl() + "/admin/projects/" + projectId;
            users.findByRoleIn(List.of(Role.ADMIN, Role.AGENT)).stream()
                    .filter(u -> !u.getId().equals(me.userId()))
                    .forEach(u -> email.sendStageReply(u.getEmail(), saved.getAuthorName(), projectName, key.label(), excerpt, link));
        }

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