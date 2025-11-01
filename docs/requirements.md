# Scala + Angular Cats Template — Requirements and Design

## Goals
- Provide a single-project template (one SBT module) that combines a functional Scala backend and an Angular frontend.
- Use idiomatic functional Scala with Cats and cats-effect for effects, http4s for HTTP, Doobie for PostgreSQL access (Skunk swap path documented), and log4cats for contextual/structured JSON logging.
- Ship a minimal TODO app end-to-end (API + Angular UI) with authentication.
- Support smooth local DX (Angular dev server with proxy to backend) and production build that serves compiled Angular assets from the backend.
- Deploy to Heroku with PostgreSQL add-on, proper configuration, health/readiness endpoints, and a working `sbt stage` pipeline for Scala 3.

## Related Documents
- [Design](design.md) — component breakdown, data flow, and integration diagrams.
- [Technical Decisions](tech.md) — chosen libraries, timeout defaults, and operational trade-offs.
- [Runbook](runbook.md) — on-call flows, restart procedures, configuration tuning steps.
- [Tasks](tasks.md) — delivery status and milestone planning.

## Scope
- In-scope: SBT project, backend service, Angular app, proxy-based dev setup, static asset serving in prod, DB schema/migrations, Docker/Heroku deployment scaffolding, logging/tracing, configuration, testing scaffold, basic authentication, and CI-ready scripts.
- Out-of-scope (v1): advanced observability stack (metrics exporters), multi-tenancy, feature toggles, and comprehensive UI/UX styling beyond the TODO example.

## Stakeholders
- Owner: Vega (template maintainer and primary user for pet projects).
- Contributors: Future collaborators.
- Platform: Heroku (runtime + Postgres), local dev environment.

## Assumptions
- One SBT project (not a multi-module build). Angular code lives under `ui/` but is not a separate SBT subproject. The backend is the only SBT project.
- JDK 21+ available in local and CI; Node 22 LTS for building the UI.
- Heroku stack supports running Java apps and Node for building the UI. Heroku will invoke `sbt stage` during slug build.
- PostgreSQL 14+; Heroku provides `DATABASE_URL` and may require TLS (`sslmode=require`).
- Following the Angular dev-proxy approach used in `tubescribes` (env-driven proxy target, e.g., `BACKEND_HOST`, `BACKEND_PORT`).

## Architecture Overview
- Backend: Scala 3, cats-effect 3, http4s, circe, Doobie, Flyway, log4cats + SLF4J/Logback, PureConfig. Optional natchez for tracing.
- Frontend: Angular 18 (or current LTS), RxJS; dev server with proxy; prod build emitted to `src/main/resources/static`.
- Database: PostgreSQL with migrations (Flyway) run at startup; connection pool via HikariCP.
- Logging: Structured, contextual logging (request/trace ids) propagated across services and DB.
- Deployment: Heroku with Procfile; either multi-buildpack (Node + Java) or container; config via env vars.

## Functional Requirements
1. TODO App API (backend)
   - Create TODO: POST `/api/todos` with `{ title, description?, dueDate?, completed=false }` → 201 with entity.
   - List TODOs: GET `/api/todos?limit&offset&status?` → 200 with page.
   - Get TODO: GET `/api/todos/{id}` → 200 or 404.
   - Update TODO: PUT `/api/todos/{id}` to edit fields; PATCH `/api/todos/{id}/toggle` to flip `completed`.
   - Delete TODO: DELETE `/api/todos/{id}` → 204.
   - Health: GET `/health` (liveness), GET `/ready` (readiness: DB reachable, migrations applied).
   - Authentication:
     - Signup: POST `/api/auth/signup` with `{ email, password }` → 201.
     - Login: POST `/api/auth/login` with `{ email, password }` → 200 with JWT (Bearer).
     - Me: GET `/api/auth/me` (requires Bearer) → current user profile.
     - Authorization: All TODO endpoints are per-user and require valid JWT (except health/readiness).

2. TODO App UI (Angular)
   - Views: list, create, edit, toggle, delete.
   - Use Angular HttpClient to call backend via dev proxy; JWT stored in memory (or localStorage in dev) and sent via `Authorization: Bearer` header.
   - Basic form validation; optimistic UI optional.

3. Static Assets in Production
   - Angular production build emitted to backend resources (`src/main/resources/static`).
   - Backend serves index.html and static assets; SPA routing fallback to `index.html`.

4. Local Development Flow
   - Start backend on `localhost:8080` (configurable).
   - Start Angular dev server on `localhost:4200` using a proxy file `ui/src/proxy.conf.js` that forwards `/api` to backend using `BACKEND_HOST` and `BACKEND_PORT` env vars (mirroring `tubescribes`).
   - Optional SBT hook/task to spawn the Angular dev server automatically during `sbt run` in dev mode.

5. Deployment
   - Heroku deploy with Procfile using native-packager stage: `web: target/universal/stage/bin/<app> -Dhttp.port=$PORT` (preferred) or assembly jar fallback.
   - `sbt stage` must: (1) run Angular prod build into `src/main/resources/static`; (2) stage backend binary.
   - Apply DB migrations on startup; handle `DATABASE_URL` parsing (host, port, db, user, password, SSL).
   - Runtime config exclusively through env vars with sane defaults for dev.

## Non-functional Requirements
- Reliability: migrations run once per deploy; safe startup ordering.
- Performance: modest footprint; non-blocking IO; reasonable p99 latency for simple CRUD (<150ms locally).
- Security: CORS restricted to known dev origin in dev; disabled/unnecessary in prod (same origin). Use parameterized SQL and Doobie `Fragments` (no string concat).
- Observability: structured JSON logs with correlation id; request/response logging with PII-safe redaction; optional tracing with natchez (e.g., Honeycomb/OTLP later).
- Testability: unit and integration tests; deterministic DB tests via test containers optional.
- Maintainability: clear project layout, minimal custom glue, documented tasks.

## Detailed Design
### Backend (Scala)
- Language/runtime: Scala 3.x; cats-effect 3.x.
- HTTP: http4s Ember or Blaze server; http4s-dsl for routes; `http4s-server` middlewares for CORS, logging, error handling.
- JSON: Circe (auto/semi-auto codecs); error responses as JSON.
- DB: Doobie (pure functional JDBC) with postgres driver; HikariCP for pooling.
  - Alternative (optional): Skunk for Postgres (pure FP + protocol). Keep Doobie by default for broader library ecosystem (see docs/tech.md for rationale and swap path).
- Migrations: Flyway triggered on startup; migration files under `src/main/resources/db/migration`.
- Config: PureConfig reads case classes from `application.conf` with overrides from env; `DATABASE_URL` parsing helper.
- Logging: log4cats with slf4j/logback backend; JSON encoder; contextual logger via `Logger.withContext(Map("requestId" -> ...))`; MDC bridge; optional natchez for tracing; correlation id middleware (header `X-Request-Id`).
- Error handling: domain errors mapped to proper HTTP status; unified error JSON body; 404/400/422/500.
- CORS: http4s CORS middleware enabled only in dev; allow `http://localhost:4200` by default.
- Static files: http4s static file service for `/` and assets; SPA fallback to `index.html` for non-`/api` routes.

### Angular (Frontend)
- Angular 18; TypeScript 5.x; RxJS 7.x.
- Directory: `ui/` within repo root; outputs to backend resources path.
- Dev proxy: `ui/src/proxy.conf.js` using `BACKEND_HOST` and `BACKEND_PORT` env vars to forward `/api` and fall back to `index.html` for SPA routes (pattern mirrored from `tubescribes`).
- Build outputs: `../src/main/resources/static` (relative to `ui/`) for main app.
- Environment files: `environment.ts` variants for dev/staging/prod as needed; base-href and deploy-url default to `/`.

### Data Model (TODO)
- Table: `todos`
  - `id` UUID (server-generated), `title` text (required), `description` text (nullable), `due_date` timestamptz (nullable), `completed` boolean (default false), `created_at` timestamptz, `updated_at` timestamptz.
- Indexes: PK on id; index on `(completed)`, `(due_date)`.

### Build & Tooling
- Single SBT project at repo root; `ui/` is a Node project managed by npm.
- SBT plugins: sbt-native-packager (preferred for Heroku `stage`), plus task glue to call `npm` for UI build. Assembly remains optional fallback.
- Dev tasks:
  - `ANGULAR_MODE=dev sbt run` launches the backend and automatically starts `npm ci` + `npm run start` in `ui/` with the proxy configured from `BACKEND_*`.
  - `npm --prefix ui start` remains an alternative if you want to run Angular separately.
- Prod build:
  - `ANGULAR_MODE=prod sbt run` runs `npm ci` + `npm run build:prod` before booting the API (assets emitted to `src/main/resources/static`).
  - `sbt stage` (preferred) or `sbt assembly` (fallback) re-run the prod build and bundle `src/main/resources/static` alongside the backend.

### Configuration & Environments
- application.conf (dev defaults) + env overrides.
- Key env vars: `HTTP_PORT` (default 8080), `ANGULAR_MODE=dev|prod` (controls SBT dev server hook), `ANGULAR_PORT` (default 4200), `BACKEND_HOST`/`BACKEND_PORT` (Angular proxy target), `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`, `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`, `DB_CONNECTION_TIMEOUT`, `JWT_SECRET`, `JWT_TTL`, `TODO_DEFAULT_PAGE_SIZE`, `TODO_MAX_PAGE_SIZE`, `LOG_LEVEL`, `TRACING_ENABLED`.
- Heroku: relies on `$PORT` (maps to `HTTP_PORT`), `DATABASE_URL` (parse and set SSL).

### Observability (Logging/Tracing)
- log4cats API, slf4j/logback implementation with JSON pattern encoder (optional) and MDC.
- Request middleware injects/propagates `X-Request-Id`; logged on each line and attached to DB logs.
- Optional natchez integration for span-scoped context and propagation; spans recorded around incoming requests and DB calls.

### Testing Strategy
- Unit: munit + munit-cats-effect; http4s route tests; JSON codec tests.
- DB: Doobie `Transactor` with TestContainers PostgreSQL; migration sanity tests ensure schema applies cleanly.
- Auth: route/middleware tests for JWT issuance/validation and protected routes.
- E2E smoke: optional CLI script that hits `/health`, creates a TODO, fetches list, toggles, deletes.

## Project Structure
```
/ (SBT root, single project)
  build.sbt
  project/
  src/
    main/
      resources/
        application.conf
        db/migration/V1__init_todos.sql
        static/                <- Angular production build output
      scala/                   <- http4s routes, services, repos
    test/
      scala/
  ui/                          <- Angular app (Node project)
    src/proxy.conf.js          <- dev proxy config using BACKEND_* env
    angular.json
    package.json
    ...
  Procfile                     <- for Heroku
  system.properties            <- Heroku JDK version if needed
```

## Deployment (Heroku)
- Option A: Multi-buildpack (recommended) with native-packager `stage`
  - Buildpacks order: `heroku/nodejs`, then `heroku/java` (or `heroku/scala`).
  - `ui` build step via `heroku-postbuild` builds Angular into backend `static/` before `sbt stage`.
  - Procfile: `web: target/universal/stage/bin/<app> -Dhttp.port=$PORT`.
- Option B: Heroku Container Registry
  - Dockerfile multi-stage: build UI with Node, copy into resources, build Scala app, final JRE image; push container.
- DB: `DATABASE_URL` parsed; set `sslmode=require` for JDBC.
- Migrations: Flyway runs at app start; fail fast and log migration status.

## CORS
- Dev: allow origin `http://localhost:4200`, methods `GET,POST,PUT,PATCH,DELETE,OPTIONS`, credentials disabled by default.
- Prod: disabled/not needed (same-origin serving) unless explicitly configured.

## Risks & Open Questions
- None outstanding for v1; see docs/tech.md for deeper tradeoffs (Doobie vs Skunk), and revisit if operational needs change.

## Tasks
| Task | Description | Assignee | Estimation |
|------|-------------|----------|------------|
| Initialize SBT single project | Create Scala 3 SBT project, dependencies, plugins |  | 2h |
| Add backend skeleton | http4s server, routes, middlewares, config loader |  | 4h |
| Add logging stack | log4cats + logback config, request id middleware |  | 2h |
| DB layer + migrations | Doobie transactor, Flyway, `todos` schema |  | 4h |
| Implement TODO API | CRUD routes, services, repo, JSON codecs |  | 5h |
| Angular app scaffold | UI project in `ui/`, basic TODO screens |  | 4h |
| Dev proxy setup | `ui/src/proxy.conf.js` with BACKEND_* env |  | 1h |
| Auth layer | JWT + bcrypt, protected routes |  | 4h |
| Heroku stage glue | Native-packager config + UI build in `stage` | ✅ | 2h |
| TestContainers PG | Integration fixtures + Flyway |  | 3h |
| Prod static serving | Angular build to `static/`, SPA fallback |  | 2h |
| SBT ⇄ UI integration | npm tasks from SBT; optional `run` hook |  | 2h |
| Heroku config | Buildpack setup, Procfile, system.properties | ✅ | 2h |
| Health/Readiness | `/health`, `/ready` endpoints with DB check |  | 1h |
| Tests | munit/cats-effect + http4s + DB smoke |  | 4h |
| Docs | README, run/deploy instructions |  | 1h |

---

## Notes
- Decisions captured in docs/tech.md (Doobie vs Skunk, Node 22 LTS, JSON logs, TestContainers, JWT auth) are reflected here and will guide implementation.
