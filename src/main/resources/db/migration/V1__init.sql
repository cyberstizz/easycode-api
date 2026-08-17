-- EasyCode Agency client portal — initial schema
create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tenancy
create table organizations (
    id           uuid primary key default gen_random_uuid(),
    name         text        not null,
    industry     text,
    website      text,
    phone        text,
    address      text,
    notes        text,
    deal_tier    text        not null default 'STANDARD',  -- STANDARD|PREFERRED|FLOOR|SPECIAL
    status       text        not null default 'ACTIVE',    -- ACTIVE|PAUSED|CHURNED
    stripe_customer_id text unique,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index idx_org_name on organizations (lower(name));

create table users (
    id            uuid primary key default gen_random_uuid(),
    org_id        uuid references organizations (id) on delete cascade, -- null for ADMIN/AGENT
    email         text        not null,
    password_hash text,
    name          text        not null,
    role          text        not null,                     -- CLIENT|AGENT|ADMIN
    status        text        not null default 'INVITED',   -- INVITED|ACTIVE|DISABLED
    last_login_at timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint uq_users_email unique (email),
    constraint ck_users_client_org check (role <> 'CLIENT' or org_id is not null)
);

create table contacts (
    id         uuid primary key default gen_random_uuid(),
    org_id     uuid        not null references organizations (id) on delete cascade,
    user_id    uuid references users (id) on delete set null,
    name       text        not null,
    email      text        not null,
    phone      text,
    role       text,
    is_primary boolean     not null default false,
    created_at timestamptz not null default now()
);
create index idx_contacts_org on contacts (org_id);
create unique index uq_contacts_org_email on contacts (org_id, lower(email));

create table invites (
    id         uuid primary key default gen_random_uuid(),
    contact_id uuid        not null references contacts (id) on delete cascade,
    email      text        not null,
    token_hash text        not null unique,
    invited_by uuid references users (id) on delete set null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index idx_invites_contact on invites (contact_id);

create table password_resets (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash text        not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);

create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    token_hash  text        not null unique,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by uuid,
    user_agent  text,
    ip          text,
    created_at  timestamptz not null default now()
);
create index idx_refresh_user on refresh_tokens (user_id);

-- ---------------------------------------------------------------- pipeline
create table leads (
    id             uuid primary key default gen_random_uuid(),
    org_id         uuid references organizations (id) on delete set null,
    business_name  text        not null,
    contact_name   text,
    email          text,
    phone          text,
    source         text,
    status         text        not null default 'NEW',  -- NEW|CONTACTED|QUALIFIED|PROPOSAL|WON|LOST
    owner_id       uuid references users (id) on delete set null,
    next_action_at timestamptz,
    est_value_cents integer,
    offered_tier   text,
    lost_reason    text,
    notes          text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index idx_leads_status on leads (status);
create index idx_leads_owner_next on leads (owner_id, next_action_at);

create table lead_activities (
    id          uuid primary key default gen_random_uuid(),
    lead_id     uuid        not null references leads (id) on delete cascade,
    user_id     uuid references users (id) on delete set null,
    type        text        not null,  -- CALL|EMAIL|MEETING|NOTE|SMS
    outcome     text,                  -- CONNECTED|VOICEMAIL|NO_ANSWER|CALLBACK|NOT_INTERESTED
    body        text,
    occurred_at timestamptz not null default now()
);
create index idx_lead_act_lead on lead_activities (lead_id, occurred_at desc);

-- ---------------------------------------------------------------- delivery
create table plans (
    id             uuid primary key default gen_random_uuid(),
    name           text        not null,
    price_cents    integer     not null,
    billing_interval text      not null default 'MONTH',
    included_hours numeric(5, 2) not null default 0,
    features       jsonb       not null default '[]'::jsonb,
    stripe_price_id text,
    active         boolean     not null default true,
    created_at     timestamptz not null default now()
);

create table projects (
    id            uuid primary key default gen_random_uuid(),
    org_id        uuid        not null references organizations (id) on delete cascade,
    name          text        not null,
    type          text,
    status        text        not null default 'ACTIVE',      -- ACTIVE|ON_HOLD|COMPLETE|CANCELLED
    current_stage text        not null default 'DISCOVERY',   -- DISCOVERY|DESIGN|DEVELOPMENT|REVIEW|LAUNCH|MAINTENANCE
    contract_cents integer,
    deposit_cents  integer,
    started_at    timestamptz,
    est_launch_at timestamptz,
    live_url      text,
    preview_url   text,
    repo_url      text,
    plan_id       uuid references plans (id) on delete set null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index idx_projects_org on projects (org_id);

create table project_stages (
    id            uuid primary key default gen_random_uuid(),
    project_id    uuid        not null references projects (id) on delete cascade,
    stage_key     text        not null,
    position      smallint    not null,
    status        text        not null default 'PENDING',  -- PENDING|ACTIVE|BLOCKED|COMPLETE
    progress_pct  smallint    not null default 0,
    started_at    timestamptz,
    completed_at  timestamptz,
    client_note   text,
    internal_note text,
    updated_at    timestamptz not null default now(),
    constraint uq_stage_per_project unique (project_id, stage_key)
);

-- ---------------------------------------------------------------- requests
create table requests (
    id               uuid primary key default gen_random_uuid(),
    org_id           uuid        not null references organizations (id) on delete cascade,
    project_id       uuid references projects (id) on delete set null,
    created_by       uuid references users (id) on delete set null,
    type             text        not null,                    -- UPDATE|QUESTION|NEW_PROJECT|BUG
    title            text        not null,
    priority         text        not null default 'NORMAL',   -- LOW|NORMAL|HIGH|URGENT
    status           text        not null default 'NEW',      -- NEW|ACKNOWLEDGED|IN_PROGRESS|NEEDS_CLIENT|DONE|DECLINED
    billing          text        not null default 'UNSET',    -- UNSET|INCLUDED|BILLABLE|DECLINED
    assignee_id      uuid references users (id) on delete set null,
    due_at           timestamptz,
    first_response_at timestamptz,
    closed_at        timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create index idx_requests_org_status on requests (org_id, status);
create index idx_requests_assignee on requests (assignee_id, status);
create index idx_requests_project on requests (project_id);

create table request_messages (
    id            uuid primary key default gen_random_uuid(),
    request_id    uuid        not null references requests (id) on delete cascade,
    author_id     uuid references users (id) on delete set null,
    body          text        not null,
    internal_only boolean     not null default false,
    created_at    timestamptz not null default now()
);
create index idx_req_msg_request on request_messages (request_id, created_at);

create table request_reads (
    request_id   uuid        not null references requests (id) on delete cascade,
    user_id      uuid        not null references users (id) on delete cascade,
    last_read_at timestamptz not null default now(),
    primary key (request_id, user_id)
);

-- ---------------------------------------------------------------- assets
create table assets (
    id          uuid primary key default gen_random_uuid(),
    org_id      uuid        not null references organizations (id) on delete cascade,
    project_id  uuid references projects (id) on delete cascade,
    stage_id    uuid references project_stages (id) on delete set null,
    request_id  uuid references requests (id) on delete cascade,
    uploaded_by uuid references users (id) on delete set null,
    r2_key      text        not null unique,
    filename    text        not null,
    mime        text,
    bytes       bigint,
    visibility  text        not null default 'CLIENT',   -- CLIENT|INTERNAL
    upload_state text       not null default 'PENDING',  -- PENDING|READY|FAILED
    caption     text,
    created_at  timestamptz not null default now()
);
create index idx_assets_project on assets (project_id, created_at desc);
create index idx_assets_request on assets (request_id);

-- ---------------------------------------------------------------- money
create table subscriptions (
    id                 uuid primary key default gen_random_uuid(),
    org_id             uuid        not null references organizations (id) on delete cascade,
    plan_id            uuid references plans (id) on delete set null,
    stripe_sub_id      text unique,
    status             text        not null default 'INCOMPLETE',
    term_months        smallint,
    current_period_end timestamptz,
    cancel_at          timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);
create index idx_subs_org on subscriptions (org_id);

create table invoices (
    id                uuid primary key default gen_random_uuid(),
    org_id            uuid        not null references organizations (id) on delete cascade,
    project_id        uuid references projects (id) on delete set null,
    number            text        not null unique,
    kind              text        not null default 'ONE_OFF',  -- DEPOSIT|MILESTONE|CHANGE_ORDER|SUBSCRIPTION|ONE_OFF
    amount_cents      integer     not null,
    amount_paid_cents integer     not null default 0,
    status            text        not null default 'DRAFT',    -- DRAFT|OPEN|PAID|VOID|UNCOLLECTIBLE
    memo              text,
    due_at            timestamptz,
    sent_at           timestamptz,
    paid_at           timestamptz,
    stripe_invoice_id text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index idx_invoices_org_status on invoices (org_id, status);

create table invoice_lines (
    id           uuid primary key default gen_random_uuid(),
    invoice_id   uuid        not null references invoices (id) on delete cascade,
    description  text        not null,
    quantity     numeric(8, 2) not null default 1,
    unit_cents   integer     not null,
    position     smallint    not null default 0
);
create index idx_inv_lines on invoice_lines (invoice_id, position);

-- in-app Payment Element: we hold the PaymentIntent ourselves
create table payments (
    id                       uuid primary key default gen_random_uuid(),
    org_id                   uuid        not null references organizations (id) on delete cascade,
    invoice_id               uuid references invoices (id) on delete set null,
    stripe_payment_intent_id text        not null unique,
    amount_cents             integer     not null,
    status                   text        not null default 'REQUIRES_PAYMENT',
    method                   text,
    failure_message          text,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now()
);
create index idx_payments_invoice on payments (invoice_id);

create table change_orders (
    id           uuid primary key default gen_random_uuid(),
    request_id   uuid        not null references requests (id) on delete cascade,
    amount_cents integer     not null,
    description  text        not null,
    status       text        not null default 'PROPOSED',  -- PROPOSED|APPROVED|DECLINED|CANCELLED
    approved_at  timestamptz,
    approved_by  uuid references users (id) on delete set null,
    invoice_id   uuid references invoices (id) on delete set null,
    created_by   uuid references users (id) on delete set null,
    created_at   timestamptz not null default now()
);
create index idx_change_orders_request on change_orders (request_id);

-- ---------------------------------------------------------------- plumbing
create table notifications (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    type        text        not null,
    title       text        not null,
    body        text,
    entity_type text,
    entity_id   uuid,
    read_at     timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_notifications_user on notifications (user_id, read_at, created_at desc);

create table audit_log (
    id          uuid primary key default gen_random_uuid(),
    actor_id    uuid references users (id) on delete set null,
    actor_email text,
    action      text        not null,
    entity_type text        not null,
    entity_id   uuid,
    diff        jsonb,
    ip          text,
    created_at  timestamptz not null default now()
);
create index idx_audit_entity on audit_log (entity_type, entity_id, created_at desc);

-- idempotency guard for Stripe webhook retries
create table stripe_events (
    id           text primary key,
    type         text        not null,
    processed_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- seed
insert into plans (name, price_cents, billing_interval, included_hours, features)
values ('Maintenance', 5000, 'MONTH', 2.00,
        '["Hosting + uptime monitoring","Security patches","Content updates","Email support","Monthly backup"]'::jsonb);
