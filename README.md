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
  service/ + impl/        domain logic
  model/{entity,dto}/    persistence + wire shapes
  repository/            Spring Data repositories
  client/                outbound HTTP (scraper, etc.)
  task/                  scheduled pollers
  config/                cross-cutting config (CORS, …)
```

## Current state

Minimal skeleton. The app boots standalone and serves:

| Method | Path             | Returns              |
|--------|------------------|----------------------|
| GET    | `/api/health`    | `{"status":"ok"}`    |
| GET    | `/api/tee-times` | `[]` (stub for now)  |

DB / JPA / mail wiring lands once the schema columns are finalized in
`greenlight-database`. Until then JPA and the datasource are intentionally left out.

## Run

```bash
./gradlew bootRun
```

Server starts on `http://localhost:8080`.

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
formatting (dates, 12-hour clock, currency, plurals) and the subject line live in
`AlertMailFactory`. The template holds no expressions beyond `th:text` / `th:if`,
so restyling and rewording stay independent.

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
| `booking-url-template` | the "Book this time" button. Placeholders `{site}` `{source}` `{slug}` `{date}` `{timeMin}` `{timeMax}` `{players}` `{holes}`. Blank ⇒ no button |
| `site-locations`       | course location line, keyed by `site` — `course` has no location column yet |

#### CPS deep-link parameters

Verified 2026-08-04 by driving the real search page through the OAB browser and
watching which `searchDate` the SPA then asked its own API for:

| URL param | Value we send | Effect |
|---|---|---|
| `Date` | `2026-08-07` (ISO) | lands on that day — **not** `searchDate`, that's the name CPS uses only on its internal API |
| `TeeOffTimeMin` / `TeeOffTimeMax` | decimal hours, ±1h around the tee time | narrows the list; the page truncates to whole hours (`17.3` → `17`) |
| `Player` | the watch's player count | pre-selects party size |
| `Hole` | `18` | pre-selects holes |

Note the capitalisation: the page reads `Date` / `Player` / `Hole` /
`TeeOffTimeMin`, while the XHR it fires afterwards uses `searchDate` /
`numberOfPlayer` / `holes` / `teeOffTimeMin`. Sending the API's spelling in the
URL silently does nothing.

> The Gradle wrapper jar is generated on first import in IntelliJ (or `gradle wrapper`).
