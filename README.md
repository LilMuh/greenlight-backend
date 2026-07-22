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

> The Gradle wrapper jar is generated on first import in IntelliJ (or `gradle wrapper`).
