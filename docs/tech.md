# Technical Decisions — Scala + Angular Cats Template

## Purpose
This document records key technical choices for the template, with rationale and implications for implementation and operations.

## DB Access: Doobie vs Skunk

### Selection Summary
- Decision: Use Doobie by default (with a clear swap path to Skunk).
- Why: Broader ecosystem/docs, JDBC compatibility (easy Heroku/JDBC URL mapping), excellent http4s/circe/cats-effect interop, mature patterns for migrations (Flyway) and TestContainers. Skunk remains a good purely functional alternative; we outline a migration path if desired later.

### Evaluation Criteria
- Functional purity and type safety
- Ecosystem maturity, docs, examples, community support
- Operational fit (Heroku, `DATABASE_URL`, TLS)
- Performance characteristics and pooling behavior
- Testing support (TestContainers)
- Developer ergonomics (error messages, debugging, logging)

### Doobie (JDBC)
- Pros:
  - Mature, widely adopted; rich docs and examples in Scala 3 with cats-effect 3.
  - Direct JDBC integration makes Heroku `DATABASE_URL` → JDBC config straightforward.
  - Works seamlessly with HikariCP for pooling; predictable operational behavior.
  - Strong integration patterns with Flyway, http4s, circe; good fragment composition and prepared statements.
  - Great, composable testing stories: in-memory H2 for some tests (when SQL portable) and TestContainers for Postgres parity.
- Cons:
  - JDBC layer adds indirection; some Postgres-native features (COPY, advanced types) can require extra work.
  - SQL string interpolation/typechecking is not fully compile-time (but parameter binding is safe and ergonomic).

### Skunk (Native Postgres protocol)
- Pros:
  - Pure FP Postgres driver; avoids JDBC; strong typed codecs and prepared statements; excellent streaming story with fs2.
  - No external connection pool library required; resource-safe sessions.
  - Excellent ergonomics around composability and protocol-level control.
- Cons:
  - Smaller ecosystem and fewer production battle stories than Doobie.
  - Some advanced features support is evolving (e.g., COPY and certain extensions) and fewer off-the-shelf examples.
  - `DATABASE_URL` parsing and TLS config need custom parsing (not hard, but less plug-and-play than JDBC).

### Decision
For a template emphasizing pragmatic FP with rich ecosystem support and Heroku-friendly configuration, Doobie is the best default. We keep the architecture layered (algebras/interpreters), enabling a later swap to Skunk with minimal surface-area changes (mostly in the repository/DAO layer and transactor wiring).

### Implementation Plan (Doobie)
- Dependencies: `doobie-core`, `doobie-hikari`, `doobie-postgres`, `postgresql` driver, `hikariCP`, `flyway-core`.
- Transactor: `HikariTransactor[IO]` created via `Resource`, configured from PureConfig; supports SSL params derived from `DATABASE_URL` on Heroku.
- Migrations: Flyway on startup; fail-fast when migrations are missing or invalid.
- Repos: Tagless final algebras (e.g., `TodoRepo[F[_]]`) with Doobie interpreters using `ConnectionIO` programs lifted via `Transactor`.
- Observability: log4cats for app logs; Doobie log handlers configured to capture SQL execution, timing, and arguments at DEBUG; MDC carries `requestId`.
- Testing: `testcontainers-scala-postgresql` for integration tests; run Flyway, then run tests against the container; seed data using plain SQL or Doobie programs.

### Swap Path to Skunk (Optional)
- Replace Doobie repositories with Skunk interpreters using typed codecs.
- Replace transactor wiring with Skunk `SessionPool` in `Resource`.
- Keep service and algebra interfaces the same; only concrete interpreters and wiring change.
- Update `DATABASE_URL` parser to yield Skunk params (host, port, db, user, password, ssl=true/false).

## Node.js LTS Selection
- Decision: Use Node 22 LTS.
- Rationale: Current LTS with long runway; Angular 18 supports Node 18/20/22. Heroku and local dev environments support Node 22. We’ll pin engines to `^22` while allowing `>=20` in CI if needed.

## Logging Format
- Decision: JSON logs by default in all environments (readable single-line JSON). Dev can optionally enable a human-friendly pattern.
- Stack: log4cats API, SLF4J/Logback backend with JSON encoder; include `level`, `timestamp`, `logger`, `message`, `requestId`, and optional `traceId`/`spanId` (natchez-ready).

## TestContainers
- Decision: Include TestContainers for Postgres integration tests.
- Tooling: `testcontainers-scala-postgresql` with munit + munit-cats-effect; lightweight fixtures to auto-provision container, run Flyway migrations, and expose `Transactor` to tests.

## Authentication
- Decision: JWT-based stateless auth using `pdi-jwt` for token handling and `org.mindrot:jbcrypt` for password hashing.
- Rationale: Works well on Scala 3, simple, explicit, and integrates cleanly with http4s middleware. Keeps FP style without depending on heavier security frameworks. Later we can add refresh tokens or cookie-based sessions.
- Design:
  - Endpoints: `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/auth/me`.
  - Storage: `users` table with unique email, bcrypt-hashed password, created/updated timestamps.
  - Middleware: Bearer token validator; extracts `sub` (user id) and injects `UserCtx` into request context for protected routes (TODO CRUD becomes per-user).
  - CORS: in dev, allow Angular origin. In prod, same-origin; tokens can be in `Authorization: Bearer` header.

## Heroku Build — `stage` Task
- Requirement: Heroku invokes `sbt stage` during slug compilation.
- Plan:
  - Use sbt-native-packager or add a custom `stage` task that:
    1) Builds Angular UI to `src/main/resources/static` (running `npm --prefix ui ci` and `npm --prefix ui run build:prod`).
    2) Builds the backend (either `universal:stage` via native packager or `assembly` and copies artifacts accordingly).
  - Procfile runs: `web: target/universal/stage/bin/<app> -Dhttp.port=$PORT` (native-packager) or `java -Dhttp.port=$PORT -jar target/scala-*/app-assembly.jar` (assembly path). We’ll ship the native-packager path for best Heroku `stage` integration.

## Ports and Proxy
- Backend port: 8080 (configurable via `HTTP_PORT`), Angular dev: 4200.
- Proxy env variables: `BACKEND_HOST`, `BACKEND_PORT`; Angular `ui/src/proxy.conf.js` mirrors tubescribes behavior and logs the target.

## Library Versions (initial targets)
- Scala 3.x (latest stable at project creation time)
- cats-effect 3.x, http4s 0.23.x or 1.x (depending on stability at init time), circe 0.14.x
- Doobie 1.0.x, Flyway 9/10.x, HikariCP latest stable
- log4cats 2.x, logback-classic 1.4/1.5.x
- Angular 18.x, RxJS 7.x, Node 22 LTS
- TestContainers-scala 0.41+ (aligned with TestContainers Java core)

## Future Enhancements
- Optional natchez tracing (OTLP exporter) and correlation with logs
- Metrics via Micrometer or Dropwizard + http4s instrumented middlewares
- Skunk variant branch demonstrating repository swap
- COPY support or bulk operations examples (Doobie and/or Skunk)
