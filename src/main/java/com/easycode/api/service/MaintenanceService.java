package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.MaintenanceSchedule;
import com.easycode.api.domain.MaintenanceVisit;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.Project;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.MaintenanceScheduleRepository;
import com.easycode.api.repo.MaintenanceVisitRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.ProjectRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance scheduling.
 *
 * <p>Two rules shape everything here.
 *
 * <p><b>Due dates are anchored, not relative.</b> The rhythm is
 * {@code anchorOn + n * cadenceDays}. Completing a visit three weeks late does not
 * push the next one three weeks out — the schedule keeps its beat and the backlog
 * stays visible. When a completion is so late that the next date is already in the
 * past, the date rolls forward in whole cadence steps and records how many periods
 * were missed, so the number is on the record instead of being quietly absorbed.
 *
 * <p><b>The client never sees a due date.</b> They see a report after the fact.
 * A published schedule turns being two days late into a broken promise; a report
 * of work done is evidence of value. Only {@link #reportsFor} is reachable from
 * the portal, and it returns completed visits only.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);
    private static final String DUE = "DUE";
    private static final String DONE = "DONE";
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    private final AccessService access;
    private final MaintenanceScheduleRepository schedules;
    private final MaintenanceVisitRepository visits;
    private final ProjectRepository projects;
    private final OrganizationRepository orgs;
    private final ContactRepository contacts;
    private final EmailService email;
    private final AppProperties props;
    private final AuditService audit;

    public MaintenanceService(
            AccessService access,
            MaintenanceScheduleRepository schedules,
            MaintenanceVisitRepository visits,
            ProjectRepository projects,
            OrganizationRepository orgs,
            ContactRepository contacts,
            EmailService email,
            AppProperties props,
            AuditService audit) {
        this.access = access;
        this.schedules = schedules;
        this.visits = visits;
        this.projects = projects;
        this.orgs = orgs;
        this.contacts = contacts;
        this.email = email;
        this.props = props;
        this.audit = audit;
    }

    /** Today in the business's timezone, not the server's. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public record Row(
            MaintenanceVisit visit,
            MaintenanceSchedule schedule,
            String projectName,
            String orgName,
            UUID orgId,
            long daysLate) {}

    public record Board(List<Row> overdue, List<Row> today, List<Row> week, List<Row> later, long activeSchedules) {}

    // ------------------------------------------------------------ schedule

    @Transactional(readOnly = true)
    public Optional<MaintenanceSchedule> scheduleFor(AuthPrincipal me, UUID projectId) {
        access.requireStaff(me);
        access.project(me, projectId);
        return schedules.findByProjectId(projectId);
    }

    /**
     * Creates or updates the rhythm for a project, and makes sure exactly one open
     * visit exists. Called by staff from the project editor, and automatically when
     * a subscription goes active.
     */
    @Transactional
    public MaintenanceSchedule upsert(
            AuthPrincipal me, UUID projectId, Integer cadenceDays, LocalDate anchorOn, Boolean active, String notes) {

        access.requireStaff(me);
        Project project = access.project(me, projectId);

        MaintenanceSchedule s = schedules.findByProjectId(projectId).orElseGet(MaintenanceSchedule::new);
        boolean isNew = s.getId() == null;
        if (isNew) {
            s.setProjectId(projectId);
            s.setOrgId(project.getOrgId());
            s.setAnchorOn(today());
        }
        if (cadenceDays != null) {
            if (cadenceDays < 1 || cadenceDays > 365) {
                throw ApiException.badRequest("Cadence must be between 1 and 365 days");
            }
            s.setCadenceDays(cadenceDays.shortValue());
        }
        if (anchorOn != null) {
            s.setAnchorOn(anchorOn);
        }
        if (active != null) {
            s.setActive(active);
        }
        if (notes != null) {
            s.setNotes(notes.isBlank() ? null : notes.trim());
        }
        MaintenanceSchedule saved = schedules.save(s);

        if (saved.isActive()) {
            ensureOpenVisit(saved);
        }
        audit.record(me, isNew ? "maintenance.start" : "maintenance.update", "project", projectId,
                Map.of("cadenceDays", String.valueOf(saved.getCadenceDays()),
                        "anchorOn", String.valueOf(saved.getAnchorOn()),
                        "active", String.valueOf(saved.isActive())));
        return saved;
    }

    /**
     * Called when a subscription becomes active. Silent and idempotent — a client who
     * already has a rhythm keeps it, and a failure here must never break a payment.
     */
    @Transactional
    public void startForOrgIfAbsent(UUID orgId) {
        try {
            for (Project p : projects.findByOrgIdOrderByCreatedAtDesc(orgId)) {
                if (schedules.findByProjectId(p.getId()).isPresent()) {
                    continue;
                }
                MaintenanceSchedule s = new MaintenanceSchedule();
                s.setProjectId(p.getId());
                s.setOrgId(orgId);
                s.setAnchorOn(today());
                s.setCadenceDays((short) 14);
                s.setActive(true);
                ensureOpenVisit(schedules.save(s));
                log.info("Maintenance schedule opened for project {} (subscription active)", p.getId());
            }
        } catch (Exception e) {
            log.error("Could not auto-start maintenance for org {}", orgId, e);
        }
    }

    /** The first scheduled date on or after today, from the anchor. */
    private LocalDate firstDueOnOrAfter(MaintenanceSchedule s, LocalDate from) {
        LocalDate anchor = s.getAnchorOn();
        int cadence = Math.max(1, s.getCadenceDays());
        if (!anchor.isBefore(from)) {
            return anchor;
        }
        long elapsed = ChronoUnit.DAYS.between(anchor, from);
        long periods = (elapsed + cadence - 1) / cadence;
        return anchor.plusDays(periods * cadence);
    }

    private MaintenanceVisit ensureOpenVisit(MaintenanceSchedule s) {
        return visits.findFirstByProjectIdAndStatusOrderByDueOnAsc(s.getProjectId(), DUE)
                .orElseGet(() -> openVisit(s, firstDueOnOrAfter(s, today()), (short) 0));
    }

    private MaintenanceVisit openVisit(MaintenanceSchedule s, LocalDate dueOn, short missed) {
        MaintenanceVisit v = new MaintenanceVisit();
        v.setScheduleId(s.getId());
        v.setProjectId(s.getProjectId());
        v.setOrgId(s.getOrgId());
        v.setDueOn(dueOn);
        v.setStatus(DUE);
        v.setMissedCycles(missed);
        return visits.save(v);
    }

    // --------------------------------------------------------------- board

    /** Everything open, bucketed. The answer to "what's on the menu this week". */
    @Transactional(readOnly = true)
    public Board board(AuthPrincipal me, int horizonDays) {
        access.requireStaff(me);
        LocalDate now = today();
        LocalDate horizon = now.plusDays(Math.max(1, horizonDays));

        List<Row> rows = new ArrayList<>();
        for (MaintenanceVisit v : visits.findByStatusAndDueOnLessThanEqualOrderByDueOnAsc(DUE, horizon)) {
            schedules.findById(v.getScheduleId()).filter(MaintenanceSchedule::isActive).ifPresent(s -> {
                Project p = projects.findById(v.getProjectId()).orElse(null);
                if (p == null) {
                    return;
                }
                rows.add(new Row(v, s, p.getName(),
                        orgs.findById(v.getOrgId()).map(Organization::getName).orElse("—"),
                        v.getOrgId(),
                        Math.max(0, ChronoUnit.DAYS.between(v.getDueOn(), now))));
            });
        }
        rows.sort(Comparator.comparing(r -> r.visit().getDueOn()));

        LocalDate weekEnd = now.plusDays(7);
        return new Board(
                rows.stream().filter(r -> r.visit().getDueOn().isBefore(now)).toList(),
                rows.stream().filter(r -> r.visit().getDueOn().isEqual(now)).toList(),
                rows.stream().filter(r -> r.visit().getDueOn().isAfter(now) && !r.visit().getDueOn().isAfter(weekEnd)).toList(),
                rows.stream().filter(r -> r.visit().getDueOn().isAfter(weekEnd)).toList(),
                schedules.findByActiveTrue().size());
    }

    /** The one open visit for a project, if the schedule is running. */
    @Transactional(readOnly = true)
    public Optional<MaintenanceVisit> openVisitFor(AuthPrincipal me, UUID projectId) {
        access.requireStaff(me);
        access.project(me, projectId);
        return visits.findFirstByProjectIdAndStatusOrderByDueOnAsc(projectId, DUE);
    }

    /** Count of open visits already past their date — the number for the Today page. */
    @Transactional(readOnly = true)
    public long overdueCount() {
        return visits.countByStatusAndDueOnLessThan(DUE, today());
    }

    // ------------------------------------------------------------ complete

    /**
     * Marks a visit done, publishes the report to the client, emails them, and opens
     * the next visit on the anchored rhythm.
     */
    @Transactional
    public MaintenanceVisit complete(AuthPrincipal me, UUID visitId, String clientReport, String internalNote) {
        access.requireStaff(me);
        MaintenanceVisit v = visits.findById(visitId)
                .orElseThrow(() -> ApiException.notFound("Maintenance visit"));
        Project project = access.project(me, v.getProjectId());

        if (DONE.equals(v.getStatus())) {
            throw ApiException.conflict("That visit is already marked done");
        }
        if (clientReport == null || clientReport.isBlank()) {
            throw ApiException.badRequest("Write what you did — the client sees this, and it's the whole point");
        }

        v.setStatus(DONE);
        v.setCompletedAt(Instant.now());
        v.setCompletedBy(me.userId());
        v.setCompletedByName(Optional.ofNullable(me.name()).filter(n -> !n.isBlank()).orElse(me.email()));
        v.setClientReport(clientReport.trim());
        v.setInternalNote(internalNote == null || internalNote.isBlank() ? null : internalNote.trim());
        MaintenanceVisit done = visits.save(v);

        MaintenanceSchedule s = schedules.findById(v.getScheduleId()).orElse(null);
        if (s != null && s.isActive()) {
            int cadence = Math.max(1, s.getCadenceDays());
            LocalDate next = v.getDueOn().plusDays(cadence);
            short missed = 0;
            // Badly behind: roll forward whole periods and keep the count on the record
            // rather than letting the backlog compound invisibly.
            while (next.isBefore(today())) {
                next = next.plusDays(cadence);
                missed++;
            }
            openVisit(s, next, missed);
        }

        notifyClient(project, done);
        audit.record(me, "maintenance.complete", "project", project.getId(),
                Map.of("visitId", done.getId().toString(),
                        "dueOn", String.valueOf(done.getDueOn()),
                        "daysLate", String.valueOf(Math.max(0, ChronoUnit.DAYS.between(done.getDueOn(), today())))));
        return done;
    }

    private void notifyClient(Project project, MaintenanceVisit done) {
        String link = props.getBaseUrl() + "/portal/project";
        String excerpt = ProjectService.excerpt(done.getClientReport());
        contacts.findByOrgId(project.getOrgId()).stream()
                .filter(c -> c.getUserId() != null && c.getEmail() != null)
                .forEach(c -> email.sendMaintenanceReport(c.getEmail(), project.getName(), excerpt, link));
    }

    // --------------------------------------------------------------- client

    /** Completed visits for the portal. Never returns an open visit or a due date. */
    @Transactional(readOnly = true)
    public List<MaintenanceVisit> reportsFor(AuthPrincipal me, UUID projectId) {
        access.project(me, projectId);
        return visits.findByProjectIdAndStatusOrderByCompletedAtDesc(projectId, DONE);
    }
}
