# Feature Design — Todo Auth Fullflow

## 1. Architecture Overview
The feature builds upon the existing http4s backend and Angular frontend. We introduce:
- A password reset subsystem (token issuance, storage, and confirmation) on the backend with an email delivery abstraction.
- Angular flows for signup/login/password reset alongside a refreshed Todo management experience.

Key components:
- **Backend**
  - `PasswordResetService` (tagless algebra) with Doobie interpreter storing tokens in Postgres.
  - `EmailService` trait with `LoggingEmailService` (dev) and `ExternalEmailService` (prod-ready, stubbed to interface with future provider).
  - Extensions to existing auth routes (`AuthRoutes`) to handle password reset request/confirm endpoints.
  - Todo routes/services updated for new UI requirements (filters, inline edits).
- **Frontend**
  - Angular standalone pages: `SignupPage`, `LoginPage`, `PasswordResetRequestPage`, `PasswordResetConfirmPage`, `TodoListPage`.
  - Auth state managed via signals; `authGuard` ensures protected routes.
  - `EmailService` is backend-only; UI triggers flows via API calls.

## 2. Backend Design

### 2.1 Password Reset Flow
- **Tokens**: store in table `password_reset_tokens` with columns:
  - `id` UUID (primary key)
  - `user_id` UUID (FK → users)
  - `token` CHAR(64) (hex-encoded random 32 bytes)
  - `expires_at` TIMESTAMP WITH TIME ZONE
  - `consumed_at` TIMESTAMP WITH TIME ZONE (nullable)
  - `created_at` TIMESTAMP WITH TIME ZONE (default now)
- **Service operations**:
  - `requestReset(email: EmailAddress): F[Unit]`
    - Lookup user; if not found, no-op (prevent enumeration).
    - Generate secure token (32 bytes random) + expiry (default 1 hour).
    - Persist token (insert row, mark previous tokens consumed).
    - Call `EmailService.sendPasswordReset(email, token, resetUrl)`.
  - `confirmReset(token: String, newPassword: PlainTextPassword): F[Unit]`
    - Fetch token by value; ensure not expired/consumed.
    - Hash new password via existing `PasswordHasher`.
    - Update user password; mark token consumed.
    - Invalidate other outstanding tokens for the user.
- **Email Service abstraction** (`EmailService` trait):
  ```scala
  trait EmailService[F[_]]:
    def sendPasswordReset(email: String, resetUrl: Uri, token: String): F[Unit]
  ```
  - `LoggingEmailService` logs payload at INFO (for dev/tests).
  - `ExternalEmailService` (prod stub) reads provider config (`EMAIL_PROVIDER`, `EMAIL_API_KEY`, etc.) and (for now) logs a warning if unset. Real integration addressed later.

### 2.2 Routes & Controllers
- Add to `AuthRoutes`:
  - `POST /api/auth/password-reset/request`
    - Body: `{ "email": "user@example.com" }`
    - Always returns 202 `{ "status": "ok" }`.
  - `POST /api/auth/password-reset/confirm`
    - Body: `{ "token": "...", "password": "newSecret" }`
    - Returns 204 on success; 400/422 on validation failure; 404 if token invalid.
- Extend error ADT for password reset errors (`ResetTokenExpired`, `ResetTokenInvalid`).
- Update `Routes` wiring to include new endpoints via combined router.

### 2.3 Todo Enhancements
- Backend already enforces ownership; ensure pagination/filter params match UI (status filter, search).
- Provide additional validation errors via `ApiError` details.
- Ensure service and route tests cover new cases (e.g., `status=open`, `dueDate` ordering).

### 2.4 Configuration
- Add to `application.conf` under `app.email`:
  ```hocon
  app {
    email {
      provider = ${?EMAIL_PROVIDER}
      provider = "logging"
      api-key = ${?EMAIL_API_KEY}
      from-address = ${?EMAIL_FROM_ADDRESS}
      reset-url-base = ${?EMAIL_RESET_URL_BASE} # e.g., https://app.example.com/reset-password
      token-ttl = 1h
    }
  }
  ```
- `AppConfig` gains `EmailConfig`.
- `PasswordResetService` receives `EmailConfig` (TTL, reset URL base).

### 2.5 Data Model & Migration
- New Flyway migration `V2__password_reset_tokens.sql`:
  ```sql
  CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
  CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
  ```
- Potential optional cleanup job (not in scope) to purge old tokens.

### 2.6 Security Considerations
- Tokens are single-use; consumed immediately after success.
- Rate limiting: rely on future rate-limiting middleware; document per requirements.
- Ensure responses for invalid tokens do not reveal existence.

### 2.7 Testing
- **Unit**: `PasswordResetServiceSpec` verifying token lifecycle, expiry, email call.
- **Integration**: Extend TestContainers suite to cover request + confirm flow.
- Mock `EmailService` in tests to capture payloads.

## 3. Frontend Design

### 3.1 Routing & Guards
- Routes (standalone):
  - `/signup` → `SignupPage`
  - `/login` → `LoginPage`
  - `/password-reset/request` → `PasswordResetRequestPage`
  - `/password-reset/confirm/:token` → `PasswordResetConfirmPage`
  - `/app/todos` → `TodoListPage` (protected by `authGuard`)
- `authGuard` ensures JWT present; on missing/expired, redirect to `/login`.
- Use Angular router `provideRouter` with `loadComponent` for lazy pages.

### 3.2 State Management
- `AuthService` manages JWT and user profile using signals.
- Tokens stored in memory + `localStorage` fallback (guarded by environment).
- `TodoService` uses HttpClient; results exposed via signals for list, filters.
- Use Angular Signals for forms; reactive forms for validation.

### 3.3 UI Components
- `SignupPage`: form with email/password/confirm; client-side validation; on submit, calls backend and auto-login.
- `LoginPage`: email/password; remember me option (stores token in localStorage).
- `PasswordResetRequestPage`: enters email; success message regardless of validity.
- `PasswordResetConfirmPage`: new password + confirmation; success leads to login route.
- `TodoListPage`: list with create/edit modals, filters (status, search), pagination controls.
- Shared components for forms, error banners.

### 3.4 Error Handling
- Map `ApiError` codes to user-friendly messages.
- Display field-level errors from `details` payload when available.
- Global interceptor for 401 to redirect to login.

### 3.5 Testing
- Component tests using Angular Testing Library.
- Service tests mocking HttpClient to ensure correct API calls.
- E2E smoke script updated to exercise signup → login → todo CRUD → password reset (future cross-team effort).

## 4. Deployment & Operations
- Update README/runbook with new configuration (`EMAIL_*`, reset URLs).
- Document dev behaviour (emails logged) vs. prod (requires provider configuration).
- Ensure CI runs Angular tests (`npm --prefix ui test`) and backend tests (`sbt test`).
- Provide sample `.env.example` snippet for new settings.

## 5. Rollout Plan
1. **Phase 1**: Backend scaffolding (email service, password reset service, migration, API routes, unit/integration tests).
2. **Phase 2**: Frontend auth pages (signup/login/reset) with test coverage.
3. **Phase 3**: Todo UI completion & integration tests; update smoke script.
4. **Phase 4**: Documentation updates (README, runbook), final verification.

## 6. Open Questions / Follow-ups
- Confirm production email provider (SendGrid vs. SES) — placeholder for now.
- Decide on storing reset tokens hashed vs. plaintext. For now store hashed (e.g., SHA-256) to avoid leakage; update design accordingly.
- Consider refresh-token support as future enhancement.

## Approval
- **Status:** Pending review
- **Approvers:** Vega / product owner
