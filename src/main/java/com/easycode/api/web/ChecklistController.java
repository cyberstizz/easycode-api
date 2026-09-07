package com.easycode.api.web;

import com.easycode.api.domain.ProjectChecklistItem;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.ChecklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Internal delivery checklists. Under /v1/admin because a client must never see
 * one — there is deliberately no portal-side equivalent of this controller.
 */
@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','AGENT')")
public class ChecklistController {

    private final ChecklistService checklists;

    public ChecklistController(ChecklistService checklists) {
        this.checklists = checklists;
    }

    public record ItemView(
            UUID id, String stageKey, int position, String title, String guidance,
            boolean emphasis, boolean done, String doneByName, Instant doneAt, String note) {

        static ItemView of(ProjectChecklistItem i) {
            return new ItemView(
                    i.getId(), i.getStageKey(), i.getPosition(), i.getTitle(), i.getGuidance(),
                    i.isEmphasis(), i.isDone(), i.getDoneByName(), i.getDoneAt(), i.getNote());
        }
    }

    public record ApplyRequest(@NotBlank String projectType, boolean replace) {}

    public record UpdateItemRequest(Boolean done, String note) {}

    /** Which templates exist, newest version of each. */
    @GetMapping("/checklist-templates")
    public Map<String, Object> templates() {
        return Map.of("items", checklists.options());
    }

    @GetMapping("/projects/{projectId}/checklist")
    public Map<String, Object> get(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID projectId) {
        return body(checklists.forProject(me, projectId));
    }

    @PostMapping("/projects/{projectId}/checklist")
    public Map<String, Object> apply(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID projectId,
            @Valid @RequestBody ApplyRequest req) {
        return body(checklists.apply(me, projectId, req.projectType(), req.replace()));
    }

    @PatchMapping("/checklist-items/{itemId}")
    public ItemView update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID itemId,
            @RequestBody UpdateItemRequest req) {
        return ItemView.of(checklists.update(me, itemId, req.done(), req.note()));
    }

    /** Items plus per-stage counts, so the UI doesn't have to tally them itself. */
    private Map<String, Object> body(List<ProjectChecklistItem> rows) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (ProjectChecklistItem i : rows) {
            int[] c = counts.computeIfAbsent(i.getStageKey(), k -> new int[2]);
            c[1]++;
            if (i.isDone()) {
                c[0]++;
            }
        }
        List<Map<String, Object>> stages = counts.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "stageKey", e.getKey(), "done", e.getValue()[0], "total", e.getValue()[1]))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows.stream().map(ItemView::of).toList());
        out.put("stages", stages);
        out.put("done", rows.stream().filter(ProjectChecklistItem::isDone).count());
        out.put("total", rows.size());
        return out;
    }
}