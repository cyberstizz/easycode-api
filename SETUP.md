# Setup — from a zip on your desktop to a running API

Follow this top to bottom. Nothing here is throwaway; every step lands in the real project.

Notation: `$` means "type this in a terminal, without the `$`."

---

## Phase 0 — Check what's already on your machine

Open a terminal and run these three. You're looking at the *output*, not the command.

```
$ java -version
$ mvn -version
$ git --version
```

**What you need:** Java 17 or higher, Maven 3.9 or higher, Git any version.

If `java` is missing or the number is below 17:
- macOS: `brew install openjdk@21` then follow the `brew` note it prints about symlinking.
- Windows: install **Eclipse Temurin 21** from adoptium.net, take the `.msi`, tick "Set JAVA_HOME".

If `mvn` is missing:
- macOS: `brew install maven`
- Windows: easiest is to skip Maven entirely and use IntelliJ IDEA Community (free), which ships
  its own copy. If you go that route, everywhere below that says `mvn X`, use IntelliJ's Maven
  panel on the right edge instead.

Java 21 is fine even though the project targets 17 — 17 is the *floor*, not the ceiling.

---

## Phase 1 — Put the project where you want it

1. Download `easycode-api.zip` from the chat and unzip it.
2. Move the resulting `easycode-api` folder into your Java projects folder. So you end up with
   something like `~/Projects/Java/easycode-api`.
3. In the terminal, go there. That folder is "the project root" every command below assumes:

```
$ cd ~/Projects/Java/easycode-api
$ ls
```

You should see `pom.xml`, `src`, `README.md`, `SETUP.md`, `run.sh`, `.env.example`.

If `ls` doesn't show `.env.example`, run `ls -a` — files starting with a dot are hidden by default.
That dot-prefix convention matters in a minute.

---

## Phase 2 — Create the GitHub repo and connect this folder to it

### 2a. Make the empty repo on GitHub

1. Go to github.com and click **+** (top right) → **New repository**.
2. Owner: your account. Repository name: `easycode-api`.
3. Visibility: **Private**.
4. **Do not** tick "Add a README", "Add .gitignore", or "Choose a license." Leave all three off.
   You already have those files locally, and starting the remote with its own commits creates a
   conflict you'd then have to untangle.
5. Click **Create repository**. GitHub shows you a setup page with a URL like
   `https://github.com/<you>/easycode-api.git`. Keep that tab open.

### 2b. Connect your local folder to it

Back in the terminal, still inside the project root:

```
$ git init
$ git add .
$ git status
```

Stop and read the `git status` output before going further. You should see roughly 115 files staged.
**You should NOT see `.env` anywhere in that list** (it doesn't exist yet, so it won't — but check
again after Phase 4). `.gitignore` already excludes it, which is the entire reason secrets go in a
`.env` file rather than in the code.

```
$ git commit -m "EasyCode client portal API: initial backend"
$ git branch -M main
$ git remote add origin https://github.com/<you>/easycode-api.git
$ git push -u origin main
```

Replace `<you>` with your GitHub username. If it asks for a password, GitHub no longer accepts your
account password over HTTPS — you need a **Personal Access Token**: GitHub → your avatar →
Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token,
tick the `repo` scope, copy it, paste it as the password. Or install the `gh` CLI and run
`gh auth login`, which handles it for you.

Refresh the GitHub page. Your code is there. From now on: `git add .`, `git commit -m "..."`,
`git push`.

---

## Phase 3 — Create the Supabase project

1. supabase.com → **New project**. Name it `easycode`. Region: **East US (North Virginia)** or
   whichever is closest to you.
2. It generates a **database password**. Copy it into a password manager immediately — Supabase
   shows it once. If you lose it you can reset it, but you'll have to update it everywhere.
3. Wait for provisioning (~2 min).
4. Click **Connect** at the top of the dashboard. You'll see several connection strings.
   Choose **Session pooler**. It looks like:

   ```
   postgresql://postgres.abcdefghijklm:[YOUR-PASSWORD]@aws-0-us-east-1.pooler.supabase.com:5432/postgres
   ```

   Copy *your* actual string. Don't reuse the example above — the region and the project reference
   are different for every project.

5. That one string splits into the three values the app wants:

   | Your `.env` key | Comes from | Example |
   |---|---|---|
   | `DB_URL` | the part after `@`, prefixed with `jdbc:postgresql://` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` |
   | `DB_USERNAME` | the part between `//` and `:` | `postgres.abcdefghijklm` |
   | `DB_PASSWORD` | the password from step 2 | |

**Why Session pooler and not Direct connection?** Direct connection is IPv6-only on new Supabase
projects, and Railway's builders don't always have IPv6. Session pooler is IPv4 and behaves like a
normal Postgres connection, which is what Flyway and Hibernate expect. The Transaction pooler on
port 6543 would break both — don't use it.

### Do NOT paste the SQL file into Supabase

This is the one thing that's different from how you set up Unis. Read the next phase before you
touch the SQL editor.

---

## Phase 4 — The `.env` file, and what an environment variable actually is

### What's going on conceptually

Your code needs values it must not contain: a database password, a Stripe key, a signing secret.
If those sat in `application.yml`, they'd be in your Git history forever the moment you pushed.

So the code contains **placeholders**. Open `src/main/resources/application.yml` and you'll see:

```yaml
  datasource:
    url: ${DB_URL}
```

`${DB_URL}` means "when you boot, go look up a variable named `DB_URL` and put its value here."

An **environment variable** is just a labeled value that a running program can read. It exists
*inside a process*. When you run the app, the app is a process; it inherits variables from whatever
started it. It is **not** a system-wide setting, it does not persist after the program exits, and
nothing in this project modifies your shell profile or your OS.

A **`.env` file** is a plain text file holding those `KEY=VALUE` pairs, sitting next to your code,
excluded from Git. It's a convenient place to keep them. Here's the part people trip on:

> **Spring Boot does not read `.env` on its own.** Some frameworks do. Spring doesn't. Something has
> to read the file and hand the values to the process.

That is the *only* thing this line does:

```
export $(grep -v '^#' .env | xargs)
```

Read it right to left: `grep -v '^#' .env` prints the file minus comment lines → `xargs` mashes it
into one space-separated line → `export` defines each pair as a variable in *this terminal session*,
so the app you launch next inherits them. Close the terminal and they're gone.

I've since replaced that line with `run.sh`, which does the same job more safely (it survives values
containing `#` or spaces, which the `xargs` version can mangle). Use the script.

### Do it

```
$ cp .env.example .env
```

Open `.env` in your editor. Fill in the database values from Phase 3. Then generate the JWT secret:

```
$ openssl rand -base64 48
```

This prints 64-ish random characters to your screen. That's all it does — it doesn't save anything,
set anything, or touch your system. Copy the output and paste it after `JWT_SECRET=` in `.env`.

**What the JWT secret is for:** when someone logs in, the API hands the browser a signed token
saying "this is Charles, he's an ADMIN, expires in 15 minutes." Every later request presents that
token. The API needs to know the token is one *it* issued and hasn't been edited — so it signs the
token with this secret and verifies the signature on the way back in. Anyone who knows the secret
can forge a token for any user. Hence: random, long, never in Git. It's one value, one time; you
don't need to remember it or type it again.

Leave everything else in `.env` as it is for now. R2, Stripe and Resend can stay blank — the app
boots without them, and `RESEND_ENABLED=false` prints invite links to your terminal instead of
emailing them.

Now, before you go further:

```
$ git status
```

`.env` **must not** appear. If it does, stop and tell me.

---

## Phase 5 — First run, and what Flyway is

### What Flyway is

For Unis, you wrote SQL and pasted it into the Supabase SQL editor by hand. That works, but nothing
records *which* SQL you ran, so six months later you can't tell whether staging and production have
the same schema, and a new laptop means repeating it from memory.

Flyway is a migration runner bundled into this app. On startup it:

1. connects to the database,
2. creates a bookkeeping table called `flyway_schema_history`,
3. looks in `src/main/resources/db/migration/` for files named `V1__`, `V2__`, `V3__`…,
4. runs any it hasn't run before, in order, and records each one.

So the schema is **code, in the repo, versioned with everything else**. When you need a new column
later, you don't edit `V1__init.sql` — you add `V2__add_whatever.sql` and restart. Flyway sees V1 is
done, runs only V2.

**This is why you don't paste `V1__init.sql` into the Supabase editor.** If you do, the tables exist
but `flyway_schema_history` is empty, so on boot Flyway tries to create them again and fails on
"relation already exists." Let the app do it. If you've already pasted it, drop the tables and start
clean, or tell me and I'll walk you through baselining instead.

### Run it

```
$ ./run.sh
```

(Windows without a bash shell: `mvn spring-boot:run`, after setting the variables in your IDE's run
configuration — IntelliJ: Run → Edit Configurations → Environment variables → paste the contents
of `.env` there.)

First run downloads dependencies — a few minutes, lots of scrolling. Then look for Flyway lines
reading something like "Migrating schema public to version 1 - init" and "Successfully applied 1
migration", then Tomcat starting on port 8080.

Check it in a second terminal:

```
$ curl http://localhost:8080/v1/public/health
```

Go look at Supabase → Table Editor. Every table is there. That's the moment the two halves connect.

**If it fails**, the useful part of the error is usually the last few lines, not the first. Paste
them to me — connection refused, authentication failed, and schema validation errors each mean
something quite different.

---

## Phase 6 — Create your admin account

There's no signup page anywhere in this system, by design: clients get in by invitation only. So
the very first account has to come from somewhere else. That's what these two do:

```
BOOTSTRAP_ADMIN_EMAIL=charles@yourdomain.com
BOOTSTRAP_ADMIN_PASSWORD=pick-something-strong
```

Yes — **in the `.env` file**, same as everything else. Add them, stop the app (`Ctrl+C`), start it
again. On boot it checks whether an admin with that email exists; if not, it creates one with that
password hashed. It's idempotent, so a restart won't duplicate or overwrite anything.

Then **delete those two lines from `.env`** and restart once more. The account persists in the
database; the credentials no longer sit in a file.

Log in:

```
$ curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"charles@yourdomain.com","password":"pick-something-strong"}'
```

You get back an `accessToken` and your user record. **That's the whole backend proven** — auth,
database, and schema all working together. Everything in `API_EXAMPLES.md` works from here; paste
that token as `Authorization: Bearer <token>`.

---

## Phase 7 — The outside accounts (do these after Phase 6 works, not before)

Each one is independent. The app runs without any of them; each just lights up one feature.

| Account | Unlocks | What you'll paste into `.env` |
|---|---|---|
| **Stripe** (test mode) | invoices, deposits, subscriptions | `STRIPE_SECRET_KEY` (Developers → API keys → Secret key, starts `sk_test_`) |
| **Cloudflare R2** | client file uploads | `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` — create a private bucket named `easycode-client-assets`, then an API token scoped to it |
| **Resend** | real invite and invoice emails | `RESEND_API_KEY`, and set `RESEND_ENABLED=true` |

Resend needs a verified sending domain, so it's genuinely blocked until you own one. Stripe and R2
aren't — do those whenever. The webhook secret (`STRIPE_WEBHOOK_SECRET`) comes later, when there's a
public URL for Stripe to call; skip it for now.

---

## Phase 8 — Deploy to Railway (once the domain exists)

1. railway.app → New Project → **Deploy from GitHub repo** → pick `easycode-api`.
2. It detects Maven and builds. No Dockerfile needed.
3. Variables tab → add every line from your `.env`, with three changes:
   - `JWT_COOKIE_SECURE=true`
   - `JWT_COOKIE_SAMESITE=None`
   - `CORS_ORIGINS=https://<your-netlify-domain>`
4. Settings → Networking → Generate Domain. You get `something.up.railway.app`. Later, point
   `api.<yourdomain>` at it.
5. Stripe → Developers → Webhooks → add endpoint `https://<that-domain>/v1/stripe/webhook`, select
   `payment_intent.succeeded`, `payment_intent.payment_failed`, `invoice.paid`,
   `customer.subscription.*`. Copy the signing secret into `STRIPE_WEBHOOK_SECRET` on Railway.

Railway runs the same Flyway migration against the same Supabase database on its first boot — and
finds it already applied, so it does nothing. That's the point of the history table.

---

## What to take back to the other thread

Once Phase 6 passes, the frontend thread needs exactly three things from you:

1. **The base URL** — `http://localhost:8080` while you're building locally, the Railway URL after
   Phase 8.
2. **`API_EXAMPLES.md`** — every endpoint, with real request bodies.
3. **Your admin credentials**, so it can wire the login screen against a real account.

That's the handoff. You don't need Phases 7 or 8 finished to start the frontend — it can build
against localhost all week.
