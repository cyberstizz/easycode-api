# easycode-api

Spring Boot 3.5 / Java 17 backend for the EasyCode Agency client portal.
React → **this** → Postgres. Same shape as the Unis backend, so it's one mental model across both products.

---

## The decisions this implements

| # | Decision | Where it lives |
|---|---|---|
| 1 | The frontend **never** touches Supabase directly. Tenancy is enforced in Java, not RLS. | `AccessService` — every client-scoped fetch goes through it |
| 2 | Own auth, not Supabase Auth: admin creates the client, invite email, client sets password | `AuthService`, `OrgService.invite` |
| 3 | **One** Request object (UPDATE / QUESTION / NEW_PROJECT / BUG) with the thread inside it and a billing disposition on the front | `ClientRequest`, `RequestService` |
| 4 | **In-app** payment form (your override) — Stripe Payment Element, not hosted checkout | `BillingService`, `StripeWebhookService` |
| 5 | Six stages, always all six, in order | `StageKey`, `ProjectService` |

Plus the things the audit flagged: the contact form now validates and **fails loudly** (`POST /v1/public/contact`),
every state change worth arguing about writes an `audit_log` row, and internal notes are filtered out of
every client-facing response rather than being hidden in the UI.

---

## Before you run it

**This has not been compiled.** There was no Maven or Maven Central access in the environment it was
written in — same situation as the Unis messaging backend pass. Run `mvn -q clean verify` locally first.

Three version pins to check against what resolves for you (`pom.xml` properties):

- `stripe.version` 26.12.0 — the code deliberately avoids `invoice.payment_intent`, which recent Stripe
  API versions removed, so it should survive a bump.
- `awssdk.version` 2.28.16 — **do not jump past 2.30** without testing. Newer SDKs add default integrity
  checksums that break R2 presigned PUTs.
- `jjwt.version` 0.12.6 — 0.12.x API (`Jwts.parser().verifyWith(...)`), not the older 0.11.x style.

## Run it

**Setting this up for the first time? Read [SETUP.md](SETUP.md) instead** — it walks the whole thing
click by click, from the zip to a logged-in admin account, and explains what each piece is doing.

The short version, once you've been through it:

```bash
cp .env.example .env          # fill in DB + JWT_SECRET at minimum
openssl rand -base64 48       # -> paste into JWT_SECRET
./run.sh                      # loads .env into the process, then starts the app
```

Flyway builds the whole schema on first boot — **don't** paste `V1__init.sql` into the Supabase SQL
editor yourself, or the migration will collide with your hand-made tables. Set `BOOTSTRAP_ADMIN_EMAIL`
and `BOOTSTRAP_ADMIN_PASSWORD` in `.env` once to create the first ADMIN, then delete those two lines.

With `RESEND_ENABLED=false`, every email — including invite and reset links — is printed to the log
instead of being sent. That's how you test the invite flow before a domain exists.

## Deploy

```
api.<domain>   → Railway    (this service)
db             → Supabase   (NEW project — do not share the Unis one)
files          → R2         (bucket easycode-client-assets, private)
```

Railway: set every var from `.env.example`, plus `CORS_ORIGINS=https://<your-netlify-domain>` and
`JWT_COOKIE_SECURE=true`, `JWT_COOKIE_SAMESITE=None`. The refresh token is an httpOnly cookie, so the
frontend must send `credentials: 'include'` on `/v1/auth/*`.

---

## API

Access token in `Authorization: Bearer`. Refresh token in an httpOnly, rotating, DB-backed cookie —
replaying a revoked one revokes every session for that user.

**Auth** `/v1/auth`
```
POST   /login                     POST /refresh              POST /logout
GET    /me                        GET  /invites/{token}      POST /invites/accept
POST   /password/forgot           POST /password/reset
```

**Client portal**
```
GET    /v1/portal/home            action surface: open requests, unread, amount due, recent files
GET    /v1/projects               GET  /v1/projects/{id}     (tracker rail + stages)
GET    /v1/requests               POST /v1/requests          GET /v1/requests/{id}
POST   /v1/requests/{id}/messages POST /v1/requests/{id}/read
POST   /v1/change-orders/{id}/approve      (one button, by design)
POST   /v1/assets/presign         POST /v1/assets/{id}/complete   GET /v1/assets/{id}/url
GET    /v1/billing/summary        GET  /v1/invoices/{id}
POST   /v1/invoices/{id}/payment-intent    -> client_secret for the Payment Element
POST   /v1/billing/setup-intent   POST /v1/subscriptions
```

**Admin console**
```
GET    /v1/admin/dashboard        today's queue
GET    /v1/admin/organizations    POST … /{id}/contacts   POST …/contacts/{id}/invite
POST   /v1/admin/projects         PATCH …/{id}/stages/{stageKey}   POST …/{id}/advance
PATCH  /v1/requests/{id}          triage: status, billing disposition, assignee, due date
POST   /v1/requests/{id}/change-orders
POST   /v1/admin/invoices         POST …/{id}/send   POST …/{id}/void
GET    /v1/admin/leads/board      GET …/due   POST …/{id}/activities   POST …/{id}/convert
```

**Public** — `POST /v1/public/contact` (marketing form → real lead + email), `GET /v1/public/health`

### File upload is three calls
`POST /v1/assets/presign` → `PUT` the bytes straight to the returned R2 URL → `POST /v1/assets/{id}/complete`.
Rows stay `PENDING` and invisible until step three, so a failed upload leaves no ghost in the gallery.

### Payments
One-off money (deposit, milestone, change order) is **our** invoice and **our** PaymentIntent:
`POST /v1/invoices/{id}/payment-intent` returns a `client_secret`, the Payment Element confirms it,
and the invoice is only marked paid by `payment_intent.succeeded` on the webhook. Never mark it paid
on the frontend's say-so.

Recurring money is two steps so the UI stays in-app: `setup-intent` collects the card, then
`POST /v1/subscriptions` with the resulting `paymentMethodId`. Set `plans.stripe_price_id` for the
$50/mo plan before selling one.

Webhook: `https://api.<domain>/v1/stripe/webhook`, events
`payment_intent.succeeded`, `payment_intent.payment_failed`, `invoice.paid`,
`customer.subscription.*`. Signature-verified and idempotent via the `stripe_events` table.

**On ACH vs cards** (you left this to me): turn on cards + Link + Apple/Google Pay, leave ACH off for now.
ACH only wins on fees above roughly $500, and it settles in days with a reversal window — on a $100–$200
deposit that's slower money for a rounding error. Revisit it if the $1,200 full-build-with-50%-down rung
starts closing regularly; enabling it later is a dashboard toggle, no code change.

---

## Schema notes

`V1__init.sql` is the whole model. Two things worth knowing:

- `organizations.deal_tier` carries `STANDARD | PREFERRED | FLOOR | SPECIAL` — the ladder, including the
  comp/referral clients that sit off it entirely.
- `subscriptions.term_months` is where the 24 goes on the preferred rung.

## What isn't here yet

Deliberate gaps, roughly in the order I'd close them:

1. **Integration tests.** Two pure unit suites ship (`JwtServiceTest`, `TokensTest`). The tenancy rules in
   `AccessService` are the thing most worth a Testcontainers suite — one client must never read another's
   invoice, and that deserves a red test if it ever breaks.
2. **Rate limiting** on `/v1/auth/login`, `/password/forgot` and `/v1/public/contact`.
3. **Notifications** — the table and entity exist, nothing writes to them; email carries the load today.
4. **Invoice numbering** reads max-then-increment. Fine for one person; add a sequence before a second
   admin is creating invoices at the same moment.
5. `GET /v1/admin/invoices` with no `orgId` loads all invoices unpaged.
