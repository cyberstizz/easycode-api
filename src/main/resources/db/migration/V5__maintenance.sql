-- Maintenance scheduling. Internal-facing scheduling, client-facing reports.
--
-- The client never sees when the next visit is due. They see a report once a
-- visit is completed. That asymmetry is deliberate: a published due date turns
-- being two days late into a broken promise, while a report of work done is
-- pure evidence of value.
--
-- Due dates are anchored, not relative to completion. anchor_on + n*cadence_days
-- defines the rhythm, so finishing a visit late does not quietly reset the
-- clock — the backlog stays visible.
create table maintenance_schedules (
    id            uuid primary key default gen_random_uuid(),
    project_id    uuid        not null unique references projects (id) on delete cascade,
    org_id        uuid        not null references organizations (id) on delete cascade,
    cadence_days  smallint    not null default 14,
    anchor_on     date        not null,
    active        boolean     not null default true,
    notes         text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint ck_cadence check (cadence_days between 1 and 365)
);
create index idx_msched_org on maintenance_schedules (org_id);

-- One open visit per project at a time. Completing it creates the next.
create table maintenance_visits (
    id              uuid primary key default gen_random_uuid(),
    schedule_id     uuid        not null references maintenance_schedules (id) on delete cascade,
    project_id      uuid        not null references projects (id) on delete cascade,
    org_id          uuid        not null references organizations (id) on delete cascade,
    due_on          date        not null,
    status          text        not null default 'DUE',   -- DUE | DONE | SKIPPED
    completed_at    timestamptz,
    completed_by    uuid references users (id) on delete set null,
    completed_by_name text,
    client_report   text,          -- shown to the client, markdown
    internal_note   text,          -- never shown to the client
    missed_cycles   smallint not null default 0,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_mvisit_due on maintenance_visits (status, due_on);
create index idx_mvisit_project on maintenance_visits (project_id, due_on desc);
