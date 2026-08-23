-- ════════════════════════════════════════════════════════════════════════
--  V2 — lead analytics
--
--  V1 shipped a call LOG. This makes it a call RECORD you can count.
--
--  Free text can't be aggregated. Without structured objection tags and the
--  rung offered, there's no way to answer "which objection kills deals" or
--  "which offer actually closes" — the two questions that turn a pipeline
--  into a sales strategy.
--
--  Also renames two funnel stages to match how EasyCode actually sells:
--  QUALIFIED -> PITCHED (an offer went out) and PROPOSAL -> NEGOTIATING
--  (they pushed back and we moved down the ladder).
-- ════════════════════════════════════════════════════════════════════════

-- ── leads ───────────────────────────────────────────────────────────────

-- Why you're calling them back. Without this, next_action_at is a date with
-- no context and you re-read the whole history before every call.
alter table leads add column if not exists next_action_note text;

-- Rename the two middle stages. Values are stored as text, so this is a
-- data update — no type change, no downtime.
update leads set status = 'PITCHED'     where status = 'QUALIFIED';
update leads set status = 'NEGOTIATING' where status = 'PROPOSAL';

comment on column leads.status is
  'NEW|CONTACTED|PITCHED|NEGOTIATING|WON|LOST';

-- ── lead_activities ─────────────────────────────────────────────────────

-- How long the call ran. Nobody remembers afterward, and connect-time is the
-- clearest signal of which conversations are actually going somewhere.
alter table lead_activities add column if not exists duration_seconds integer;

-- The whole point of the redesign. A Postgres text[] rather than a join table:
-- the tag vocabulary is short, fixed, and only ever read in aggregate.
alter table lead_activities add column if not exists objection_tags text[] not null default '{}';

-- Which rung of the ladder went on the table during THIS call. Distinct from
-- leads.offered_tier, which only holds the latest — this is the history, and
-- it's what tells you whether you're dropping to the floor too early.
alter table lead_activities add column if not exists rung_offered text;

comment on column lead_activities.rung_offered is
  'STANDARD|PREFERRED|FLOOR|SPECIAL — null when nothing was offered on this call';

-- Aggregating by tag means unnesting the array; GIN makes that cheap.
create index if not exists idx_lead_act_tags on lead_activities using gin (objection_tags);
create index if not exists idx_lead_act_rung on lead_activities (rung_offered)
  where rung_offered is not null;

-- BAD_NUMBER was missing from the outcome vocabulary. It matters: a dead
-- number is a data-quality problem, not a rejection, and lumping it in with
-- NO_ANSWER makes a cold list look worse than it is.
comment on column lead_activities.outcome is
  'CONNECTED|VOICEMAIL|NO_ANSWER|BAD_NUMBER|CALLBACK|NOT_INTERESTED';