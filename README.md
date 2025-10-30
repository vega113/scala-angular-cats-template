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
- Backend:
  - `sbt run` (serves on `http://localhost:8080` by default)
- Frontend (dev server with proxy):
  - `export BACKEND_HOST=localhost BACKEND_PORT=8080`
  - `npm --prefix ui install`
  - `npm --prefix ui start` (serves on `http://localhost:4200`)

Health and readiness:
- `GET http://localhost:8080/health`
- `GET http://localhost:8080/ready`

## Build & Run (Prod-like)
- Build Angular and stage backend:
  - `sbt stage` (should run Angular prod build then stage backend)
- Run staged app:
  - `target/universal/stage/bin/<app> -Dhttp.port=8080`

Alternatively, if using assembly:
- `sbt assembly`
- `java -Dhttp.port=8080 -jar target/scala-*/<app>-assembly-*.jar`

## Configuration
Environment variables (see docs/requirements.md for full list):
- `HTTP_PORT` (default 8080)
- `ANGULAR_MODE` (`dev|prod`)
- `ANGULAR_PORT` (default 4200)
- `BACKEND_HOST`, `BACKEND_PORT` (dev proxy)
- `DATABASE_URL` (Heroku-style)
- `JWT_SECRET`, `JWT_TTL`
- `LOG_LEVEL` (JSON logs)

## Testing
- Backend unit/integration: `sbt test`
- Frontend unit (if configured): `npm --prefix ui test`

## Deployment (Heroku)
- Buildpacks: `heroku/nodejs` then `heroku/java`
- `sbt stage` is invoked during slug build; Procfile runs staged binary:
  - `web: target/universal/stage/bin/<app> -Dhttp.port=$PORT`
- Postgres: use `DATABASE_URL`; enable SSL (`sslmode=require`) as needed

## Project Layout
- Backend: `src/main/scala`, `src/main/resources` (Flyway migrations, Logback, static in prod)
- Frontend: `ui/` (Angular), `ui/src/proxy.conf.js` for dev proxy
- CI: `.github/workflows/ci.yml`
- Docs: `docs/requirements.md`, `docs/design.md`, `docs/tech.md`, `docs/tasks.md`
- Collaboration: `AGENTS.md`

## Docs
- Requirements: `docs/requirements.md`
- Design: `docs/design.md`
- Technical decisions: `docs/tech.md`
- Tasks and milestones: `docs/tasks.md`

## Contributing
- See `AGENTS.md` for branching, PRs, and conflict resolution.

