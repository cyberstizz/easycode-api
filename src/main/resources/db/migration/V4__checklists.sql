-- Internal delivery checklists. Staff-only; nothing here is ever exposed to a
-- client, and nothing here drives the stage tracker or its percentage.
--
-- Templates are versioned. When a checklist document changes, the seed service
-- writes a NEW version rather than editing the old one, so a project already
-- running keeps the list it started with. Project rows also copy the title and
-- guidance text at instantiation, which means an in-flight project is immune to
-- template edits even for wording.
create table checklist_templates (
    id            uuid primary key default gen_random_uuid(),
    project_type  text        not null,      -- WEB | MOBILE | AI_AGENT
    label         text        not null,
    version       int         not null,
    content_hash  text        not null,
    created_at    timestamptz not null default now(),
    unique (project_type, version)
);

create table checklist_template_items (
    id           uuid primary key default gen_random_uuid(),
    template_id  uuid    not null references checklist_templates (id) on delete cascade,
    stage_key    text    not null,
    position     int     not null,
    title        text    not null,
    guidance     text,
    emphasis     boolean not null default false
);
create index idx_tpl_items on checklist_template_items (template_id, stage_key, position);

create table project_checklist_items (
    id            uuid primary key default gen_random_uuid(),
    project_id    uuid    not null references projects (id) on delete cascade,
    template_id   uuid references checklist_templates (id) on delete set null,
    stage_key     text    not null,
    position      int     not null,
    title         text    not null,
    guidance      text,
    emphasis      boolean not null default false,
    done          boolean not null default false,
    done_by       uuid references users (id) on delete set null,
    done_by_name  text,
    done_at       timestamptz,
    note          text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index idx_proj_checklist on project_checklist_items (project_id, stage_key, position);