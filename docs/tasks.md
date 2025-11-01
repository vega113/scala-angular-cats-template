# Project Plan and Task Breakdown

This plan reflects the design in docs/design.md and decisions in docs/requirements.md and docs/tech.md. It emphasizes parallelizable work with clear dependencies and Definitions of Done (DoD).

## Legend
- Status: 🟢 In progress · 🟡 Planned · 🟣 Ready for review · 🔴 Blocked · ✅ Done
- Estimation units: person-hours (h)

## Milestones Overview
- M1. Foundation Setup
- M2. Backend Core Platform
- M3. Persistence & Migrations
- M4. Authentication
- M5. TODO API
- M6. Frontend (Angular)
- M7. Integration & Static Serving
- M8. Monitoring & Observability
- M9. Deployment (Heroku)
- M10. Quality & CI
- M11. Scalability & Error Handling
- M12. Documentation & Runbooks

## Parallelization Plan (High-level)
- Parallel streams possible after M1: {Backend Core (M2)} can run in parallel with {Frontend scaffold (M6)}.
- M3 depends on M2 (config/runtime) but DB schema work can begin in parallel (SQL + Flyway) with mocked transactor.
- M4 (Auth) depends on M3 (users table) and M2 (HTTP/middlewares).
- M5 (TODO API) can start once M3 is usable; UI (M6) can concurrently build views against mocked API or dev server.
- M7 (Static Serving) follows M6 build + M2 server; can be short-running.
- M8/M10/M11 can partially start earlier (logging format, CI skeleton, error taxonomy) but finalize post-M5/M7.
- M9 (Deployment) can stage early using health endpoint, then expand as features arrive.

---

## M1. Foundation Setup

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-101 | ✅ | Initialize single SBT project (Scala 3) + native-packager | 2h | — |
| T-102 | ✅ | Add Node 22 LTS setup (`.nvmrc`, engines) | 0.5h | — |
| T-103 | ✅ | Create Angular workspace in `ui/` | 1.5h | T-102 |
| T-104 | ✅ | Dev proxy file `ui/src/proxy.conf.js` (BACKEND_HOST/PORT) | 0.5h | T-103 |
| T-105 | ✅ | Logging baseline (logback JSON encoder, log4cats) | 1h | — |
| T-106 | ✅ | Configuration scaffold (PureConfig, types, env) | 1h | — |
| T-107 | ✅ | SBT `stage` pipeline (build UI then stage backend) | 2h | T-103 |
| T-108 | ✅ | AGENTS.md collaboration guide | 1h | — |

- Description: repo skeleton, tooling, stage flow.
- DoD: `sbt stage` runs; Angular builds to `src/main/resources/static`; AGENTS.md present; logback outputs JSON.
- Agent Context: touch build.sbt, project/plugins.sbt, src/main/resources/logback.xml, ui/*, Procfile, .nvmrc.

---

## M2. Backend Core Platform

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-201 | ✅ | http4s server (Ember), routes wiring | 2h | T-101, T-106 |
| T-202 | ✅ | Middlewares: request-id, JSON logging, error handler | 2h | T-105 |
| T-203 | ✅ | Health/Readiness endpoints | 1h | T-201 |
| T-204 | ✅ | CORS (dev-only allow 4200) | 0.5h | T-201 |

- DoD: `sbt run` serves `/health`, `/ready`; logs JSON with requestId; CORS active in dev.
- Agent Context: src/main/scala/.../Main.scala, HttpRoutes, middleware package.

---

## M3. Persistence & Migrations

_Status log_: 2025-10-30 – T-305 marked ✅ after migrations committed and integration tests added.

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-301 | ✅ | Flyway setup + baseline migration files | 1.5h | T-101 |
| T-302 | ✅ | Doobie transactor via HikariCP (Resource) | 2h | T-106 |
| T-303 | ✅ | DATABASE_URL parser (Heroku SSL) | 1h | T-106 |
| T-304 | ✅ | TestContainers Postgres fixture | 2h | T-302 |
| T-305 | ✅ | Migrations V1: users, todos | 2h | T-301 |

- DoD: App boots, runs migrations; integration test uses TestContainers and passes.
- Agent Context: src/main/resources/db/migration/*, src/main/scala/.../db/TransactorWiring.scala, src/test/*.

---

## M4. Authentication

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-401 | ✅ | Password hashing (bcrypt) + user repo | 2h | T-302, T-305 |
| T-402 | ✅ | JWT service (sign/verify), config | 1.5h | T-106 |
| T-403 | ✅ | Auth routes: signup, login, me | 2h | T-401, T-402, T-201 |
| T-404 | ✅ | Auth middleware (Bearer → UserCtx) | 1.5h | T-402, T-201 |
| T-405 | ✅ | Tests (unit + integration) | 2h | T-401..T-404, T-304 |

- DoD: Endpoints functional; protected test route requires JWT; tests green against TestContainers.
- Agent Context: src/main/scala/.../auth/*, routes under /api/auth, config for `JWT_SECRET`, `JWT_TTL`.

---

## M5. TODO API

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-501 | ✅ | Domain + Circe codecs | 1h | T-201 |
| T-502 | ✅ | Repo (Doobie) with pagination, filters | 2.5h | T-302, T-305 |
| T-503 | ✅ | Service validation (per-user ownership) | 1.5h | T-404, T-502 |
| T-504 | ✅ | Routes: CRUD + toggle | 2h | T-503 |
| T-505 | ✅ | Tests (repo + routes) with TestContainers | 2h | T-304, T-504 |

- DoD: All endpoints pass contract; pagination/filters work; protected by JWT; tests green.
- Agent Context: src/main/scala/.../todo/*.

---

## M6. Frontend (Angular)

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-601 | ✅ | Angular scaffold + routing | 2h | T-103 |
| T-602 | ✅ | Auth pages (login/signup) + service | 3h | T-601 |
| T-603 | ✅ | JWT interceptor + guard | 1h | T-601 |
| T-604 | ✅ | Todos pages (list/form) + service | 4h | T-601 |
| T-605 | ✅ | Dev proxy verification | 0.5h | T-104, T-201 |
| T-606 | ✅ | Build to static resources (prod) | 0.5h | T-107 |
| T-607 | ✅ | SBT run starts Angular dev server + prod static serving | 1h | T-104, T-201 |

- DoD: UI performs auth+todo flows against dev proxy; prod build lands in backend static folder.
- Agent Context: ui/src/app/*, ui/src/proxy.conf.js, angular.json, package.json.
- Implementation Notes:
  - T-602: build login and signup flows with standalone components, Angular signals, and HttpClient integration against `/api/auth` endpoints; update AuthService accordingly.
  - T-603: register functional HTTP interceptors (`withInterceptors`) and guards via `canActivateFn`; avoid reintroducing NgModules.
  - Keep widget build compatibility (Angular Elements) when altering interceptors.
  - T-604: standalone components + signals for todos list/editor, `TodoApiService` bridging to backend CRUD.
  - T-605: confirm `ANGULAR_MODE=dev sbt run` launches Angular dev proxy; README documents workflow.
  - T-606: `ANGULAR_MODE=prod sbt run`/`sbt stage` build UI into `src/main/resources/static` for backend serving.
  - T-607: ensure `sbt run`/`runMain` boots the Angular dev proxy when `ANGULAR_MODE=dev`, build assets otherwise, and serve the SPA via backend static routes for non-dev modes.

---

## M7. Integration & Static Serving

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-701 | ✅ | Static assets serving + SPA fallback | 1h | T-201, T-606 |
| T-702 | ✅ | E2E smoke script (curl-based) | 0.5h | T-404, T-504, T-701 |

- DoD: Frontend assets served by backend in prod; smoke passes (signup → login → CRUD → delete).
- Agent Context: http4s static routes, `scripts/smoke.sh` smoke script.
- Notes:
  - T-701: `Server.scala` serves resources under `static/` and falls back to `index.html` for non-API GETs, even when request `Accept` is missing.
  - T-702: `scripts/smoke.sh` drives auth + todos CRUD via curl, defaulting to `http://localhost:8080`.

---

## M8. Monitoring & Observability

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-801 | ✅ | JSON logging fields + requestId propagation | 1h | T-202 |
| T-802 | ✅ | Structured error logs + redaction policy | 1h | T-202, T-503 |
| T-803 | ✅ | Heroku log drain/filters guidance (docs) | 0.5h | T-701 |
| T-804 | ✅ | Optional natchez scaffolding (off by default) | 1.5h | T-201 |

- DoD: Logs include requestId/userId; docs explain reading logs and enabling tracing later.
- Agent Context: logback config, `LoggingMiddleware`, `ErrorHandler`, `TracingMiddleware`, docs/tech.md.
- Notes:
  - T-801: Logging middleware now emits structured start/finish events with `requestId`, `userId`, and latency; `X-User-Id` propagated from authed routes.
  - T-802: Error handler sanitizes client responses, logs structured context with reference ids.
  - T-803: README + docs/tech.md describe logging/tracing behaviour; `scripts/smoke.sh` useful for verifying log output.
  - T-804: Optional natchez scaffolding (`TracingMiddleware`, config toggle + noop entry point) ready for future exporters.

---

## M9. Deployment (Heroku)

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-901 | ✅ | Buildpacks + Procfile + system.properties | 0.5h | T-107, T-203 |
| T-902 | ✅ | Heroku env mapping (`DATABASE_URL`, `JWT_*`) | 0.5h | T-303, T-402 |
| T-903 | 🟡 | Heroku release smoke (health/readiness) | 0.5h | T-203 |

- DoD: `heroku builds:create` succeeds; app boots; health passes; logs in JSON.
- Agent Context: Procfile, system.properties, README deployment section.
- Notes:
  - T-901: `Procfile` executes staged binary; `system.properties` pins Java 21; README documents buildpack ordering.
  - T-902: README lists required config vars (`ANGULAR_MODE=prod`, `JWT_SECRET`, etc.); backend auto-detects `DATABASE_URL` & SSL.

---

## M10. Quality & CI

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-1001 | ✅ | Scalafmt + ESLint configs | 0.5h | T-101, T-103 |
| T-1002 | ✅ | CI workflow: test + build + stage | 2h | T-203, T-606 |
| T-1003 | ✅ | Pre-push checks (fmt, test) | 0.5h | T-1001 |

- DoD: CI green on PR; formatting enforced; tests run headless.
- Agent Context: `.scalafmt.conf`, `ui/.eslintrc.json`, `.github/workflows/ci.yml`, `scripts/pre-push.sh`.
- Notes:
  - T-1001: sbt-scalafmt plugin + config added; Angular ESLint config and dependencies installed.
  - T-1002: GitHub Actions workflow runs `npm ci`, lint, `sbt scalafmtCheckAll test`, and prod UI build.
  - T-1003: `scripts/pre-push.sh` automates fmt/test/lint/build ahead of pushes.

---

## M11. Scalability & Error Handling

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-1101 | ✅ | Error taxonomy + mapping (400/401/403/404/422/500) | 1h | T-202 |
| T-1102 | ✅ | Timeouts + limits (server/client/DB) | 1h | T-201, T-302 |
| T-1103 | ✅ | Connection pool sizing (HikariCP) | 0.5h | T-302 |
| T-1104 | ✅ | Rate limiting placeholder (docs) | 0.5h | T-201 |

- DoD: Central error handler; configs documented; defaults safe; guidance for scaling on Heroku dynos.
- Agent Context: `ApiResponse`, `ErrorHandler`, `application.conf` timeouts, `docs/tech.md` (timeouts & rate limiting).
- Notes:
  - T-1101: Introduced `ApiResponse`/`ApiError` helpers; routes now emit consistent error codes (400/401/403/404/409/422/500) with structured JSON.
  - T-1102: Ember server timeouts configurable (`request-header`, `idle`, `shutdown`); docs call out defaults.
  - T-1103: Hikari pool sizing now exposes `minimumIdle` + `connectionTimeout`; Heroku env vars surfaced in README/config.
  - T-1104: Rate limiting placeholder documented in `docs/tech.md` with recommended future approach.

---

## M12. Documentation & Runbooks

| ID | Status | Task | Estimate | Dependencies |
|----|--------|------|----------|--------------|
| T-1201 | ✅ | README: dev, test, deploy | 1h | T-203, T-606, T-901 |
| T-1202 | 🟢 | Runbook: on-call basics, restart, logs | 0.5h | T-801, T-903 |
| T-1203 | 🟡 | Design references crosslinks | 0.5h | — |

- DoD: Docs present, consistent with design and tech decisions.
- Agent Context: README.md, docs/*.

---

## Monitoring (Details)
- Logging: JSON with fields [ts, level, logger, msg, requestId, userId?].
- Tracing: natchez stub; spans around requests and DB (later opt-in).
- Health: `/health` liveness; `/ready` checks DB + Flyway.

## Scalability (Details)
- Stateless app; scale horizontally by dynos.
- DB pool tuned per dyno; prefer connection limits under Heroku plan thresholds.
- Use pagination on list endpoints; avoid N+1; batch where needed.

## Error Handling (Details)
- Central error encoder returns `{ error: { code, message, details } }`.
- Redact sensitive data (passwords, tokens) at boundaries.
- Map domain errors to HTTP: NotFound, BadRequest, Unauthorized, Forbidden, UnprocessableEntity, Internal.

## Working Agreement for Agents (Summary)
- Use task IDs and short names for branches, e.g., `feature/T-504-todo-routes`, `docs/T-1201-readme`.
- One PR per task; include DoD checklist and status emoji in description.
- Keep changes focused; do not refactor unrelated code.
- Run fmt/tests locally; ensure CI green before requesting review.
- If multiple agents touch same area, coordinate in PR comments and assign temporary CODEOWNER.
- Follow AGENTS.md for full collaboration rules (branching, reviews, conflict resolution, style, and tool usage).
