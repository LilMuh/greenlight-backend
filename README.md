# greenlight-backend

The GreenLight brain — a Java 21 / Spring Boot service that owns the domain logic:
it reads scraped tee times from Postgres, evaluates watch configs, and exposes a
REST API the [frontend](https://github.com/LilMuh/greenlight-frontend) reads from.

Part of [GreenLight](https://github.com/LilMuh/greenlight-frontend). Sibling repos:
[database](https://github.com/LilMuh/greenlight-database) ·
[scraper](https://github.com/LilMuh/greenlight-scraper).

## Stack

- Java 21, Spring Boot 3.5, Gradle (Kotlin DSL)
- PostgreSQL (schema owned by `greenlight-database`, this app only **validates** it)

## Layout

```
src/main/java/golf/
  GolfApplication.java   app entry point
  controller/            REST endpoints
  service/               domain logic
  service/mail/          alert rendering + SMTP / logging senders
  model/{entity,dto}/    persistence + wire shapes
  repository/            Spring Data repositories
  client/                outbound HTTP (scraper, etc.)
  task/                  scheduled pollers
  config/                cross-cutting config (CORS, …)
src/main/resources/
  templates/mail/        alert mail HTML
```

## How a round works

Scraping is driven entirely by `watch_config` — nothing gets polled unless somebody
is watching it.

1. A watch records **weekdays**, not dates — "every Saturday morning" stays true next
   month. `WatchWindow` turns those weekdays back into the concrete dates inside
   `[today, today + 7]`; scraping and matching both read that same list.
   `ScrapePlanService` then turns the active watches into `(source, site, date, courses)`
   jobs. Same site + same day ⇒ one job with several courses. No active watch ⇒ empty
   plan ⇒ the round is skipped without opening a browser.
2. `TeeTimeScrapeTask` runs that plan every 15 min and POSTs each job to
   [greenlight-scraper](https://github.com/LilMuh/greenlight-scraper), which owns the
   writes to `tee_time`. `fixedDelay` (not `fixedRate`) so rounds can never overlap.
3. Notification dedup uses **no ledger table**. The task snapshots each watch's set of
   matching tee-time ids *before* the round and recomputes it *after*; only the rising
   edge — matching now, not matching before — gets a mail. A slot that gets booked out
   stops at `available_seats = 0`, leaves the set, and re-entering it later is naturally
   a fresh edge.
4. Creating a watch scrapes its weekdays' dates first, *then* sends a baseline mail listing what
   already matches. Both happen after commit, on an async thread, so
   `POST /api/watch-configs` returns immediately (`WatchBootstrapListener`).

## API

| Method | Path | Returns |
|--------|------|---------|
| GET | `/api/health` | `{"status":"ok"}` |
| GET | `/api/courses` | course list for the picker (incl. `imageUrl`) |
| GET | `/api/tee-times?date=&course=` | bookable slots for a day, `course` = slug, optional |
| GET | `/api/matches` | per active watch, how many slots currently match |
| GET | `/api/watch-configs` | all watches, newest first |
| POST | `/api/watch-configs` | batch create: N course ids + one shared config ⇒ N watches |
| PUT | `/api/watch-configs/{id}` | update one |
| DELETE | `/api/watch-configs/{id}` | `204` |
| POST | `/api/scrape` | run a round now instead of waiting for the timer |
| GET | `/api/notifications/preview` | the alert mail as HTML, sample data — see below |
| POST | `/api/notifications/test?to=` | send a sample alert to one address |

## Run

Needs Postgres with the schema from
[greenlight-database](https://github.com/LilMuh/greenlight-database) already migrated —
this app runs `ddl-auto: validate` and will refuse to start if entities and tables
disagree. Connection settings are in `application.yml`.

```bash
./gradlew bootRun
```

Server starts on `http://localhost:8080`. Scraping needs greenlight-scraper up on
`:8090`; without it the rounds just log failures and everything else still serves.

## Email notifications

Two modes, picked by `greenlight.mail.mode`:

| Mode  | Behaviour                                              |
|-------|--------------------------------------------------------|
| `dev` | **default** — logs recipient + subject only, sends nothing |
| `prod`| really sends over SMTP                                 |

To actually send, set these env vars before `bootRun`:

```bash
GREENLIGHT_MAIL_MODE=prod
GREENLIGHT_SMTP_HOST=smtp.gmail.com     # default; change for another provider
GREENLIGHT_SMTP_PORT=587                # default (STARTTLS)
GREENLIGHT_SMTP_USER=you@gmail.com
GREENLIGHT_SMTP_PASSWORD=<app password> # Gmail: an App Password, NOT your login password
GREENLIGHT_MAIL_FROM=you@gmail.com      # defaults to GREENLIGHT_SMTP_USER
```

Gmail needs 2-Step Verification on, then an [App Password](https://myaccount.google.com/apppasswords).
In `prod` mode the app **fails to start** if host / username / password / from are missing —
better a loud crash than silently sending nothing.

Verify the channel without waiting for a scrape round:

```bash
curl -X POST "http://localhost:8080/api/notifications/test?to=you@example.com"
```

### The mail template

Alert mails are HTML only (no `text/plain` part). Layout lives in
`src/main/resources/templates/mail/tee-time-alert.html`; the wording, all
formatting (dates, 24-hour clock, currency, plurals) and the subject line live in
`AlertMailFactory`. The template holds no expressions beyond `th:text` / `th:if`,
so restyling and rewording stay independent.

The body is grouped by play date — one block per day, slots in time order, and a
single booking link per day rather than one per slot. Two subject wordings, picked
by `AlertMailFactory.Kind`; the body is identical either way:

| Kind | Sent when | Subject |
|------|-----------|---------|
| `BASELINE` | a watch was just created | `…: 3 tee times match your alert` |
| `NEW` | rising edge after a scrape round | `…: 3 new tee times just opened` |

Three ways to see it, cheapest first:

```bash
./gradlew test          # writes build/mail-preview/*.html — open in a browser, no DB needed
```

```bash
# app running: renders the same template with sample data, refresh after each edit
open http://localhost:8080/api/notifications/preview        # ?kind=baseline for the other one
```

```bash
# prod mode: check how your own mail client renders it
curl -X POST "http://localhost:8080/api/notifications/test?to=you@example.com"
```

Two knobs in `application.yml` under `greenlight.mail`:

| Key | Purpose |
|-----|---------|
| `booking-url-template` | the per-day `BOOK →` link. Placeholders `{site}` `{source}` `{slug}` `{date}` `{timeMin}` `{timeMax}` `{players}` `{holes}`. Blank ⇒ no link, the date groups still render |
| `site-locations`       | the `· Vancouver, BC` after the course name, keyed by `site` — `course` has no location column yet. Unmapped site ⇒ that bit is omitted |

#### CPS deep-link parameters

Verified 2026-08-04 by driving the real search page through the OAB browser and
watching which `searchDate` the SPA then asked its own API for:

| URL param | Value we send | Effect |
|---|---|---|
| `Date` | `2026-08-07` (ISO) | lands on that day — **not** `searchDate`, that's the name CPS uses only on its internal API |
| `TeeOffTimeMin` / `TeeOffTimeMax` | decimal hours: that day's earliest match −1h to its latest +1h | narrows the list; the page truncates to whole hours (`17.3` → `17`), so the window lands a bit wider than asked — the slots we care about are always inside it |
| `Player` | the watch's player count | pre-selects party size |
| `Hole` | `18` | pre-selects holes |

Note the capitalisation: the page reads `Date` / `Player` / `Hole` /
`TeeOffTimeMin`, while the XHR it fires afterwards uses `searchDate` /
`numberOfPlayer` / `holes` / `teeOffTimeMin`. Sending the API's spelling in the
URL silently does nothing.

> The Gradle wrapper jar is generated on first import in IntelliJ (or `gradle wrapper`).

## License

[GNU Affero General Public License v3.0](LICENSE).

You may use, modify and redistribute this code. The Affero clause adds one
condition on top of the GPL: if you run a modified version as a network service,
you must offer its source to the people using it.

All four GreenLight repositories are under the same license.
