# Scala + Angular Cats Template

Functional Scala 3 backend + Angular 18 frontend, set up as a single SBT project. Uses Cats, cats-effect, http4s, Doobie, log4cats (JSON), Flyway, and TestContainers. Angular builds to static assets served by the backend in production; during development the Angular dev server proxies to the backend.

## Features
- Idiomatic FP backend (cats-effect + http4s)
- Postgres via Doobie + Flyway migrations
- JSON logging (log4cats + Logback)
- JWT auth (signup, login, me)
- Angular dev proxy (BACKEND_HOST/BACKEND_PORT)
- Heroku-ready (`sbt stage`, Procfile, Postgres)
- TestContainers for DB integration tests

## Stack
- Scala 3, SBT, cats-effect, http4s, circe, Doobie, Flyway, log4cats
- Angular 18, RxJS 7, Node 22 LTS

## Prerequisites
- JDK 21+
- SBT
- Node.js 22.x (see `.nvmrc` once created)
- Docker (for TestContainers parity locally optional)

## Quickstart (Dev)
- One-command workflow (backend + Angular dev proxy):
  - `ANGULAR_MODE=dev sbt run`
    - Starts the Scala API on `http://localhost:8080`
    - Boots the Angular dev server on `http://localhost:4200` with the proxy already configured
- Manual alternative:
  - Backend: `sbt run`
  - Frontend: `npm --prefix ui install && npm --prefix ui start`
    - Ensure `BACKEND_HOST=localhost BACKEND_PORT=8080`

Health and readiness:
- `GET http://localhost:8080/health`
- `GET http://localhost:8080/ready`

## Build & Run (Prod-like)
- Build Angular assets & run backend with a single command:
  - `ANGULAR_MODE=prod sbt run`
    - Runs `npm ci` + `npm run build:prod` (output to `src/main/resources/static`) before starting the API
- Build and stage for deployment:
  - `sbt stage`
  - `target/universal/stage/bin/<app> -Dhttp.port=8080`

Alternatively, if using assembly:
- `sbt assembly`
- `java -Dhttp.port=8080 -jar target/scala-*/<app>-assembly-*.jar`

## Configuration
Environment variables (see docs/requirements.md for full list):
- `HTTP_PORT` (default 8080)
- `ANGULAR_MODE` (`dev|prod`; defaults to `dev`)
- `ANGULAR_PORT` (default 4200)
- `BACKEND_HOST`, `BACKEND_PORT` (dev proxy target)
- `DATABASE_URL` (Heroku-style JDBC string)
- `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA` (override defaults when not using `DATABASE_URL`)
- `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`, `DB_CONNECTION_TIMEOUT` (Hikari tuning)
- `JWT_SECRET`, `JWT_TTL`
- `TODO_DEFAULT_PAGE_SIZE`, `TODO_MAX_PAGE_SIZE`
- `LOG_LEVEL` (JSON logs)
- `EMAIL_PROVIDER`, `EMAIL_FROM_ADDRESS`, `EMAIL_API_KEY`, `EMAIL_RESET_SUBJECT`, `EMAIL_ACTIVATION_SUBJECT`, `EMAIL_ACTIVATION_URL_BASE`
- `PASSWORD_RESET_URL_BASE`, `PASSWORD_RESET_TOKEN_TTL`
- `TRACING_ENABLED` (optional natchez scaffold)

## Testing
- Backend unit/integration: `sbt test`
- Frontend unit: `npm --prefix ui test`
- End-to-end smoke (requires API running locally): `scripts/smoke.sh`
- Pre-push helper (formats, tests, lints, builds): `scripts/pre-push.sh`

## Logging & Tracing
- Logs are JSON (Logstash encoder) with per-request `requestId`, optional `userId`, and latency metrics.
- Enable verbose logging by adjusting `LOG_LEVEL`; request ids propagate via `X-Request-Id` header.
- Optional tracing scaffold (`natchez`) is disabled by default; set `TRACING_ENABLED=true` and swap the entrypoint in `com.example.app.tracing.Tracing` when wiring Jaeger/OTLP/etc.

## Deployment (Heroku)
- Buildpacks (set in this order):
  1. `heroku/nodejs` (builds the Angular UI during `sbt stage`)
  2. `heroku/java`
  ```bash
  heroku buildpacks:set heroku/nodejs
  heroku buildpacks:add --index 2 heroku/java
  ```
- `sbt stage` is invoked during slug build; the committed `Procfile` runs the staged binary:
  ```Procfile
  web: target/universal/stage/bin/scala-angular-cats-template -Dhttp.port=$PORT
  ```
- Recommended config vars (examples):
  | Config var        | Purpose                                | Example value                |
  |-------------------|----------------------------------------|------------------------------|
  | `ANGULAR_MODE`     | Serve pre-built UI (disables dev proxy)| `prod`                       |
  | `JWT_SECRET`       | Signing key for auth tokens            | `super-secret-change-me`     |
  | `LOG_LEVEL`        | Runtime log level (JSON format)        | `INFO`                       |
  | `TRACING_ENABLED`  | Enable natchez middleware (optional)   | `false`                      |
  | `DATABASE_URL`     | Provided by Heroku Postgres add-on     | *(auto-set by Heroku)*       |
- Postgres: connection details arrive via `DATABASE_URL`; SSL is auto-detected (`sslmode=require` when present).

## Project Layout
- Backend: `src/main/scala`, `src/main/resources` (Flyway migrations, Logback, static in prod)
- Frontend: `ui/` (Angular), `ui/src/proxy.conf.js` for dev proxy
- CI: `.github/workflows/ci.yml`
- Docs: `docs/requirements.md`, `docs/design.md`, `docs/tech.md`, `docs/tasks.md`, `docs/runbook.md`
- Collaboration: `AGENTS.md`

## Docs
- Requirements: `docs/requirements.md`
- Design: `docs/design.md`
- Technical decisions: `docs/tech.md`
- Tasks and milestones: `docs/tasks.md`
- On-call runbook: `docs/runbook.md`

## Contributing
- See `AGENTS.md` for branching, PRs, and conflict resolution.
