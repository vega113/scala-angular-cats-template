# Feature Requirements — Todo Auth Fullflow

## Overview
Deliver an end-to-end Todo experience that spans both backend and Angular UI, covering user onboarding, authentication, and password reset flows. The feature must ensure developer-friendly DX in dev mode while remaining production-ready with hooks for real email delivery.

## Goals
- Implement complete Todo CRUD on the backend with parity UI screens (list, create, edit, toggle, delete).
- Enable user sign up, login, and self-service password reset, surfaced through the Angular UI and backed by API endpoints.
- Provide a dummy email delivery implementation for local/dev (logs email payloads) with an abstraction that can bind to real providers (e.g., SMTP, SendGrid) in production.
- Maintain structured error responses consistent with `ApiResponse` helpers and surface actionable validation feedback in the UI.
- Ensure developer workflows remain “one command” (`ANGULAR_MODE=dev sbt run`) with updated scripts/tests.

## Non-Goals
- Implementing actual third-party email integration (will be configured later).
- Building admin features or sharing todos between users.
- Adding complex Todo features like labels, reminders, or attachments.

## User Stories
1. As a new user, I can sign up via the UI, receive feedback on validation errors, and be automatically logged in after success.
2. As an existing user, I can log in with email/password, and my JWT is stored client-side securely (in-memory or appropriate storage).
3. As a user who forgot my password, I can request a reset; the system sends a link/code via email (logged in dev). I can reset the password and log in with the new credentials.
4. As an authenticated user, I can manage my todo list: create, edit, toggle completion, delete, and view filtered lists with pagination.
5. As an operator, I can switch the email delivery implementation for production without code changes (config-driven).

## Functional Requirements
- **Backend**
  - Extend auth routes for password reset (request + confirm) with expiring tokens stored securely.
  - Todo service enforces per-user ownership, including new UI-driven fields (e.g., due date, optional description).
  - Email service abstraction with test/dev logger implementation and production interface using env-configured provider.
  - Additional endpoints:
    - `POST /api/auth/password-reset/request` → logs email content and returns generic success.
    - `POST /api/auth/password-reset/confirm` → verifies token, updates password hash, invalidates token.
  - Update integration tests (TestContainers) to cover new flows.
- **Frontend (Angular)**
  - Standalone components/pages for:
    - Signup / Login
    - Todo list management (responsive layout, filters)
    - Password reset request + confirm (two-step forms)
  - State management via Signals/RxJS, interceptors for auth token handling, route guards (`authGuard`) to protect todo routes.
  - Error presentation matching backend `ApiError` payloads.
  - Unit tests for components/services; adjust CI to run them instead of placeholder command.
- **Tooling/Docs**
  - Update README/runbook with email configuration, new env vars (`EMAIL_PROVIDER`, etc.).
  - New tasks added to `docs/tasks.md` for tracking.

## Success Metrics
- Happy path flows (signup → login → CRUD → logout) pass manual QA and automated smoke tests.
- Password reset flow succeeds and invalid tokens are rejected.
- `npm --prefix ui test` executes real tests (not placeholder) with coverage for auth services.
- Dev logs show email payloads; production mode can swap to real provider via configuration.

## Constraints & Assumptions
- Remain within existing tech stack (http4s, Doobie, Angular 18).
- Use PureConfig/env vars for email provider configuration.
- JWT secret rotation out of scope; reuse existing configuration.
- Email tokens stored either in DB or secure cache (Postgres preferred for simplicity).

## Dependencies
- Builds on Milestones M4–M7 (auth, todo API, UI scaffolding) and M11 error handling.
- Requires coordination with CI scripts to ensure Angular tests run.

## Risks & Open Questions
- Token storage strategy: single table vs. reuse existing user table fields.
- Email content templating approach (simple text vs. templating engine).
- Handling rate limiting/brute force for password reset (possibly future feature).

## Approval
- **Status:** Pending review
- **Approvers:** Product owner / template maintainer (Vega)
