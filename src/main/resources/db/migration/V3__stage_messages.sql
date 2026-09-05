-- The conversation under each stage update.
--
-- A stage already carries one client-facing update (project_stages.client_note).
-- This is the thread beneath it: the client's replies and the developer's
-- answers, in order. Modelled on request_messages so the two threads behave
-- the same way everywhere.
create table stage_messages (
    id            uuid primary key default gen_random_uuid(),
    stage_id      uuid        not null references project_stages (id) on delete cascade,
    project_id    uuid        not null references projects (id) on delete cascade,
    author_id     uuid references users (id) on delete set null,
    author_name   text        not null,
    author_role   text        not null,
    body          text        not null,
    created_at    timestamptz not null default now()
);
create index idx_stage_msg_stage on stage_messages (stage_id, created_at);

-- Read receipts: "Latavia last read this yesterday" on the developer's header.
create table stage_reads (
    stage_id   uuid        not null references project_stages (id) on delete cascade,
    user_id    uuid        not null references users (id) on delete cascade,
    read_at    timestamptz not null default now(),
    primary key (stage_id, user_id)
);