# Manual QA — Todo Auth Fullflow

Use this checklist before merging feature work to `main`. All steps assume a local dev environment with `ANGULAR_MODE=dev sbt run` so the Angular dev server and backend run together.

## 1. Signup → Activation → Login
1. Start the app: `ANGULAR_MODE=dev sbt run`.
2. Open `http://localhost:4200/auth/signup`.
3. Submit a new account (unique email, password ≥ 8 chars).
4. Observe backend logs for the activation link (`Sending account activation email`). Copy the `token` query param.
5. Confirm activation:
   ```bash
   curl -sS -X POST http://localhost:8080/api/auth/activation/confirm \
     -H 'Content-Type: application/json' \
     -d '{"token":"<token-from-log>"}' -o /dev/null -w "%{http_code}\n"
   ```
   Expect HTTP 204/200.
6. Log in with the activated account and verify you land on the todos page.
7. Manually shorten the JWT lifetime (e.g., set `JWT_TTL=1s`) and restart the app. After the token expires, trigger an authenticated action (e.g., refresh `/todos`). Confirm the UI displays the session-expired banner and redirects to the login screen.

## 2. Todo CRUD + Filters
1. Create a new todo (no due date) and confirm the list flashes the success message.
2. Create additional todos (mix of completed / open via toggle) so the list exceeds one page (~12 items).
3. Verify filter buttons:
   - `All` shows every item.
   - `Open` hides completed todos immediately after toggling.
   - `Completed` shows only done items.
4. Use pagination buttons to move forward/back; ensure the component resets to the previous page when reaching an empty last page.
5. Delete a todo on page 2 and confirm the gap is filled by refreshing the page data (no blank page).

## 3. Password Reset
1. From login screen choose "Forgot password" and request a reset for the activated account.
2. Backend logs emit `Sending password reset email` — copy the `token` value.
3. Confirm the reset with a new password:
   ```bash
   curl -sS -X POST http://localhost:8080/api/auth/password-reset/confirm \
     -H 'Content-Type: application/json' \
     -d '{"token":"<token-from-log>","password":"NewP@ss123"}' \
     -o /dev/null -w "%{http_code}\n"
   ```
   Expect HTTP 204.
4. Log in with the new password to confirm the change.

## 4. Smoke Script
1. Prepare an activated test account and set env vars:
   ```bash
   export SMOKE_EMAIL=tester@example.com
   export SMOKE_PASSWORD='SuperSecret123'
   ```
2. Run the script: `scripts/smoke.sh`.
3. Confirm it creates, toggles, and deletes a todo without errors.

## 5. Regression Sweep
- Run automated suites:
  - `npm --prefix ui test`
  - `sbt test`
- Verify Angular dev server still proxies correctly when launched via `ANGULAR_MODE=dev sbt run` (no websocket warnings beyond the standard Angular banner).
- Ensure `docs/tasks.md` statuses are up to date before requesting review.

Document anomalies detected during QA in the PR description and update this checklist if coverage changes.
