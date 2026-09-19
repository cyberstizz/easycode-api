package com.easycode.api.web;

import com.easycode.api.domain.MaintenanceSchedule;
import com.easycode.api.domain.MaintenanceVisit;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.MaintenanceService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Maintenance scheduling.
 *
 * <p>Everything under /v1/admin is staff-only and includes due dates. The single
 * portal endpoint at the bottom returns completed reports and deliberately carries
 * no schedule, no due date, and nothing about what is coming.
 */
@RestController
public class MaintenanceController {

    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    // ---------------------------------------------------------- staff views

    public record ScheduleView(
            UUID id, UUID projectId, int cadenceDays, LocalDate anchorOn, boolean active, String notes) {
        static ScheduleView of(MaintenanceSchedule s) {
            return new ScheduleView(s.getId(), s.getProjectId(), s.getCadenceDays(),
                    s.getAnchorOn(), s.isActive(), s.getNotes());
        }
    }

    /** Staff shape — carries dueOn. Never returned to the portal. */
    public record VisitView(
            UUID id, UUID projectId, UUID orgId, LocalDate dueOn, String status,
            Instant completedAt, String completedByName, String clientReport,
            String internalNote, int missedCycles) {
        static VisitView of(MaintenanceVisit v) {
            return new VisitView(v.getId(), v.getProjectId(), v.getOrgId(), v.getDueOn(), v.getStatus(),
                    v.getCompletedAt(), v.getCompletedByName(), v.getClientReport(),
                    v.getInternalNote(), v.getMissedCycles());
        }
    }

    public record UpsertSchedule(Integer cadenceDays, LocalDate anchorOn, Boolean active, String notes) {}

    public record CompleteVisit(String clientReport, String internalNote) {}

    @GetMapping("/v1/admin/projects/{projectId}/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public Map<String, Object> forProject(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID projectId) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schedule", maintenance.scheduleFor(me, projectId).map(ScheduleView::of).orElse(null));
        List<MaintenanceVisit> history = maintenance.reportsFor(me, projectId);
        out.put("history", history.stream().map(VisitView::of).toList());
        out.put("open", maintenance.openVisitFor(me, projectId).map(VisitView::of).orElse(null));
        return out;
    }

    @PutMapping("/v1/admin/projects/{projectId}/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ScheduleView upsert(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpsertSchedule body) {
        return ScheduleView.of(maintenance.upsert(
                me, projectId, body.cadenceDays(), body.anchorOn(), body.active(), body.notes()));
    }

    /** The week board. horizon defaults to 14 days so "this week" and the next cycle both show. */
    @GetMapping("/v1/admin/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public Map<String, Object> board(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "14") int horizon) {

        MaintenanceService.Board b = maintenance.board(me, horizon);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", MaintenanceService.today().toString());
        out.put("overdue", rows(b.overdue()));
        out.put("dueToday", rows(b.today()));
        out.put("thisWeek", rows(b.week()));
        out.put("later", rows(b.later()));
        out.put("activeSchedules", b.activeSchedules());
        return out;
    }

    @PostMapping("/v1/admin/maintenance/visits/{visitId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public VisitView complete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID visitId,
            @Valid @RequestBody CompleteVisit body) {
        return VisitView.of(maintenance.complete(me, visitId, body.clientReport(), body.internalNote()));
    }

    private List<Map<String, Object>> rows(List<MaintenanceService.Row> rows) {
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("visitId", r.visit().getId());
            m.put("projectId", r.visit().getProjectId());
            m.put("projectName", r.projectName());
            m.put("orgId", r.orgId());
            m.put("orgName", r.orgName());
            m.put("dueOn", r.visit().getDueOn().toString());
            m.put("daysLate", r.daysLate());
            m.put("cadenceDays", (int) r.schedule().getCadenceDays());
            m.put("missedCycles", (int) r.visit().getMissedCycles());
            return m;
        }).toList();
    }

    // --------------------------------------------------------- client view

    /**
     * What the client sees: completed reports only. No dueOn, no schedule, no
     * indication that another visit is coming.
     */
    public record ReportView(UUID id, Instant completedAt, String by, String report) {}

    @GetMapping("/v1/projects/{projectId}/maintenance-reports")
    public Map<String, Object> reports(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID projectId) {
        List<ReportView> items = maintenance.reportsFor(me, projectId).stream()
                .map(v -> new ReportView(v.getId(), v.getCompletedAt(), v.getCompletedByName(), v.getClientReport()))
                .toList();
        return Map.of("items", items);
    }
}
