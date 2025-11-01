# Runbook — On-Call Basics

This runbook covers the operational tasks needed to keep the Scala + Angular template running in production (e.g., on Heroku). It assumes you have access to the application logs, the production Postgres database, and the deployment platform.

## Related Documents
- [Requirements](requirements.md) — baseline environment assumptions and supported ops workflows.
- [Design](design.md) — service architecture and data flow referenced when diagnosing incidents.
- [Technical Decisions](tech.md) — logging, timeout, and pooling defaults that influence remediation steps.
- [Tasks](tasks.md) — milestone status; check before/after incidents for pending work that might impact operations.

## 1. Quick Reference
- **Primary endpoints:** API on `HTTP_PORT` (default 8080); health checks at `/health` and `/ready`.
- **Front-end:** Served by the backend in prod (`ANGULAR_MODE=prod`); Angular dev server runs only in local dev (`ANGULAR_MODE=dev`).
- **Database:** PostgreSQL reached via `DATABASE_URL`; migrations run automatically through Flyway on startup.
- **Authentication:** JWT with shared secret `JWT_SECRET`. Default token TTL is `JWT_TTL` seconds.
- **Logs:** JSON structured logs emitted via Logback; contain `@timestamp`, `level`, `logger_name`, `message`, `requestId`, `userId` (when authenticated).
- **Configuration:** All runtime settings set via environment variables; default values live in `src/main/resources/application.conf`.

## 2. On-Call Checklist
1. **Check Alerts:** Confirm the alert name, trigger time, and current status. Most alerts target the `/ready` endpoint or 5xx error rates.
2. **Validate Availability:** Hit `/health` (liveness) and `/ready` (readiness) directly:
   - `curl -sf https://<app-domain>/health`
   - `curl -sf https://<app-domain>/ready`
   - `/ready` failure indicates DB connectivity or migration issue.
3. **Inspect Logs:** Use `heroku logs --tail` (or equivalent) to view structured JSON. Filter by `requestId` if available.
4. **Check Database:** Ensure Postgres is reachable: `heroku pg:psql` (or `psql $DATABASE_URL`). Run simple query: `SELECT NOW();`.
5. **Confirm Recent Deploys:** `heroku releases` (or CI history) to see if a rollout correlates with the alert.
6. **Escalate Criteria:** If critical endpoints remain down for more than 10 minutes, attempt restart (section 3). Escalate to the team if restart fails or data integrity is in question.

## 3. Restart / Recovery Procedures

### 3.1 Heroku Dyno Restart
1. `heroku ps:restart web` — restarts the single web dyno.
2. Monitor logs during startup for:
   - Flyway migration summary: `"Flyway migrations executed: <n>"`.
   - Ember server bind: `"Ember-Server service bound to address..."`.
   - Angular mode log from `Main`: `App config loaded: http=..., angular=prod@4200`.
3. Verify readiness: `curl -sf https://<app-domain>/ready`.

### 3.2 Rollback to Previous Release
If the issue started after a deploy and restart does not help:
1. `heroku releases` → note the previous stable release (e.g., `v123`).
2. `heroku releases:info v123` to confirm.
3. `heroku rollback v123`.
4. Re-verify `/ready` and smoke a login/todo flow.

### 3.3 Database Maintenance
- **Connection saturation:** Check `heroku pg:info` for connection usage. Adjust `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`, or `DB_CONNECTION_TIMEOUT` if needed (see section 5).
- **Stuck queries:** Use `SELECT pid, query FROM pg_stat_activity WHERE state = 'active';` and terminate with `SELECT pg_terminate_backend(<pid>);` if necessary. Only do this if you’re sure the query is hanging.

## 4. Log Exploration
- Logs are JSON; pipe through `jq` for readability:
  ```bash
  heroku logs --tail | jq
  ```
- Key fields:
  - `requestId`: allows correlation across backend logs. If Angular dev proxy is used locally, look for `[ui-dev]` prefixes; production logging omits these entries.
  - `userId`: present when authenticated requests hit protected routes; helpful when investigating customer-specific failures.
  - `error.code` / `error.message`: produced by `ApiResponse.error`; ensures clients receive consistent formats (e.g., `todo.not-found`, `auth.invalid-credentials`).
- To isolate errors:
  ```bash
  heroku logs --tail | grep '"level":"ERROR"'
  ```
- Timeout tuning log lines appear under `org.http4s.ember.server` (server timeouts) or `com.zaxxer.hikari` (DB pool settings). Adjust corresponding env vars if timeouts are too aggressive.

## 5. Configuration Changes
Use environment variables to tune behaviour; after setting a var, restart the dyno.

| Variable | Purpose | Typical Adjustments |
|----------|---------|---------------------|
| `HTTP_PORT` | Binding port (Heroku sets automatically). | Rarely changed manually. |
| `ANGULAR_MODE` | `prod` in production. | Only switch to `dev` for local debugging; never in prod. |
| `DATABASE_URL` | Full JDBC URL used by Heroku. | Swap when rotating DB credentials or using followers. |
| `DB_MAX_POOL_SIZE` | Hikari max connections (default 8). | Reduce if Postgres plan has low connection limit; increase for larger dynos. |
| `DB_MIN_IDLE` | Keep warm connections (default 0). | Raise if experiencing cold-start latency. |
| `DB_CONNECTION_TIMEOUT` | Wait time for a connection (default 30s). | Increase if bursts exceed pool capacity. |
| `JWT_SECRET` / `JWT_TTL` | Auth token signing + expiry. | Rotate secret with downtime plan (invalidate tokens). |
| `TODO_DEFAULT_PAGE_SIZE` / `TODO_MAX_PAGE_SIZE` | Paging controls for `/api/todos`. | Tighten to reduce payload size under load. |
| `LOG_LEVEL` | Logging verbosity (`INFO` default). | Set to `DEBUG` temporarily when diagnosing issues; revert after. |
| `TRACING_ENABLED` | Enables natchez tracing scaffold. | Turn on when tracing backend requests (requires endpoint wiring). |

Apply updates:
```bash
heroku config:set LOG_LEVEL=DEBUG
heroku ps:restart web
```

## 6. Smoke Tests After Recovery
Run the smoke script (requires local env with access to the app) or manual HTTP checks:
1. `scripts/smoke.sh --base-url https://<app-domain>` (script exercises auth + TODO CRUD).
2. Manual sequence:
   - `POST /api/auth/signup` (or login existing user) to obtain token.
   - `GET /api/todos` expecting `200` and JSON payload.
   - `POST /api/todos` to create; `PATCH /api/todos/{id}/toggle`; `DELETE /api/todos/{id}`.

If any step fails, capture the `requestId` from the response headers (or error body) and cross-reference in logs.

## 7. Escalation & Communication
- **Escalate** when:
  - Incident exceeds 30 minutes without clear mitigation.
  - Data loss or migration failure is suspected.
  - Security concerns (leaked secrets, compromised JWT secret).
- **Notify** stakeholders via the agreed incident channel, including:
  - Timeline of events.
  - Current status and impact.
  - Next steps and owner.

## 8. Appendix
- **Code References:**
  - Startup config logging: `com.example.app.Main` (prints Angular mode + ports).
  - Error model: `src/main/scala/com/example/app/http/ApiResponse.scala`.
  - Hikari pool settings: `src/main/scala/com/example/app/db/TransactorBuilder.scala`.
  - Timeouts: `application.conf` (`app.http.*`).
- **Useful Commands:**
  ```bash
  # Tail logs for errors
  heroku logs --tail | grep '"level":"ERROR"'

  # View current config
  heroku config

  # Check DB connections
  heroku pg:info
  ```

Keep this document version-controlled; update whenever configuration, infrastructure, or operational processes change.
