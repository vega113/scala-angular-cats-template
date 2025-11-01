# Feature Tasks — Todo Auth Fullflow

| ID | Status | Task | Estimate | Owner | Notes |
|----|--------|------|----------|-------|-------|
| T-1301 | ✅ | Requirements: capture fullflow scope | 1h | — | Approved 2025-11-01 |
| T-1302 | ✅ | Design: architecture & UI plan | 1.5h | — | Approved 2025-11-01 |
| T-1303 | 🟣 | Backend: password reset service, tokens, routes, tests | 6h | — | Includes migration `V2__password_reset_tokens.sql`, service + integration tests |
| T-1304 | 🟣 | Backend: email service abstraction & config | 4h | — | Implement logging provider, production-ready interface, config wiring |
| T-1305 | 🟣 | Frontend: auth flows (signup/login/reset) | 6h | — | Standalone pages, guards, interceptors, unit tests |
| T-1309 | 🟢 | Backend: account activation tokens & flow | 5h | T-1303 |
| T-1310 | 🟢 | Frontend: activation UX (pending screen, activation page) | 5h | T-1305, T-1309 |
| T-1311 | 🟢 | Frontend: todo creation UX polish | 2h | T-1306 |
| T-1306 | 🟡 | Frontend: todo UI enhancements | 5h | — | List filters, CRUD UX, align with backend responses |
| T-1307 | 🟡 | Tooling & docs: tests, smoke updates, README/runbook | 3h | — | Ensure `npm --prefix ui test` real tests, docs for EMAIL_* env vars |
| T-1308 | 🟡 | Final integration, manual QA, PR wrap-up | 2h | — | End-to-end verification, update task statuses |

## Definition of Done (Feature)
- [ ] Backend password reset endpoints pass unit/integration tests and hide sensitive data.
- [ ] Email service logs payloads in dev and can be configured for real providers in prod.
- [ ] Angular UI supports signup/login/password reset with validation and auth guards.
- [ ] Todo UI matches backend API (filters, pagination, error handling).
- [ ] `npm --prefix ui test` executes meaningful tests (no placeholder script).
- [ ] README/runbook document new flows, env vars, and operational steps.
- [ ] Smoke script (or manual checklist) covers signup → login → todo CRUD → password reset.
- [ ] All tasks above moved to ✅ in this table and root `docs/tasks.md`.
