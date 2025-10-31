# Collaboration Guide for AI/Dev Agents

This repository may be worked on by multiple agents in parallel. Follow these rules to avoid stepping on each other's work and to keep changes safe and reviewable.

## Quick Reference
**Starting a new feature?**
1. Create `docs/features/<feature-key>/` with `requirements.md` → get approval
2. Write `design.md` → get approval  
3. Write `tasks.md` with granular tasks
4. Create branch `feature/T-XXX-description` from latest `main`
5. Implement, test, open PR with links to feature docs
6. Update task status emojis: 🔵 → 🟢 → 🟣 → ✅

**Key practices:**
- Scala: Tagless Final, error ADTs, query param extractors, `Resource` for lifecycle
- Angular: Standalone components, Signals, `@if/@for/@defer`, `inject()`, functional guards
- Testing: `sbt test` (backend), `npm --prefix ui test` (frontend)

## Scope and Precedence
- This AGENTS.md applies to the entire repository.
- If a subdirectory adds its own AGENTS.md, the most deeply nested file takes precedence for files in that subtree.
- Direct instructions from the user or task owner override this document.

## Branching Model
- Create a short-lived branch per task using the task ID from docs/tasks.md.
  - Examples: `feature/T-504-todo-routes`, `infra/T-901-heroku-procfile`, `docs/T-1201-readme`.
- Do not develop on `main` directly.
- Keep branches focused to the specific task.

## Commits & PRs
- Keep commits small and descriptive. Prefix commit subject with the task ID.
- Open one Pull Request (PR) per task. PR title should include the task ID and a short description.
- PR description must include:
  - Summary of changes.
  - Checklist for the task’s Definition of Done (from docs/tasks.md and the DoD Template below).
  - Any migrations or operational steps.
  - Screenshots or logs where relevant (attach Lighthouse for UI when applicable).
  - Docs: links to the feature’s `requirements.md` and `design.md` under `docs/features/<feature-key>/`.
  - Tasks linkage: link the exact line(s) in the feature’s `tasks.md` and update the status emoji there.
- Mark PRs as draft until tests pass and you’ve self-reviewed.

## Task Status Updates
- On opening a PR, set the task’s status emoji in `docs/tasks.md` to 🟢 (In progress).
- When the PR is ready for review, update to 🟣 (Ready for review).
- After merge to `main`, update the status to ✅ (Done).
- If blocked by another task or external dependency, set 🔴 (Blocked) and add a short note in the PR.
- Keep estimates as-is; optionally add actuals in the PR body for retrospective.
- For feature work, also update the per‑feature `docs/features/<feature-key>/tasks.md` table.

## Feature Docs Workflow (Requirements → Design → Tasks)
- Location & naming:
  - For every main feature, create `docs/features/<feature-key>/` with: `requirements.md`, `design.md`, `tasks.md`.
  - `<feature-key>` is lowercase, hyphenated, short (e.g., `auth-refresh-tokens`, `todo-bulk-actions`).
- Lifecycle & gates:
  - Stage 1 — Requirements: problem, measurable goals, constraints, success metrics, non‑goals. Approval required before design starts.
  - Stage 2 — Design: architecture, APIs/contracts, data model & migrations, error taxonomy, security, performance, rollout, test strategy. Approval required before implementation.
  - Stage 3 — Tasks/Execution: granular tasks with IDs, estimates, statuses, and links to PRs. Keep status synced during development.
- PR requirements:
  - PRs implementing feature tasks must link to the feature’s `requirements.md` and `design.md`, and reference the exact task row in `tasks.md`.
  - Update the status emoji in both the feature `tasks.md` and the root `docs/tasks.md` index.
- Indexing:
  - Add a one‑line entry for each feature in the root `docs/tasks.md` that links to `docs/features/<feature-key>/tasks.md`.

## Reviews & Merges
- Another agent or the task owner should review before merge.
- Squash-merge to keep history tidy (unless the owner requests preserving commits).
- Resolve conflicts by rebasing your branch on latest `main` and re-running tests.

## Parallel Work Guidance
- Before starting, check open PRs to see if nearby files are being modified.
- If file overlap is likely, coordinate in PR comments and divide scope to minimize conflicts.
- Prefer additive changes and extension points over wide refactors.

## Code Style & Safety
- Scala: idiomatic functional style with Cats; avoid shared mutable state; prefer pure algebras + interpreters.
- Angular/TS: align with Angular CLI conventions; keep services lean and components focused.
- Avoid unrelated changes (formatting, renames) outside your task.
- Include or update tests where applicable.

### Scala — Idiomatic FP Backend (Cats + http4s + Doobie)
- Architecture: Tagless Final algebras (traits with `F[_]`); keep domain pure; use `Resource[F, A]` for lifecycle.
- http4s: Keep routes thin; delegate to services. Use `QueryParamDecoderMatcher` for query params, `UUIDVar/IntVar` for paths, `AuthedRoutes` for auth. Middleware composes via `Kleisli`.
- Errors: Sealed trait ADTs for domain errors; map to HTTP consistently; provide `code` + `message` in JSON.
- Doobie: Parameterized queries; centralize `Meta/Read/Write`; mark transactions; ensure indexes; `EXPLAIN` heavy queries.
- Concurrency: `parTraverseN` with bounds; apply timeouts/retries; avoid unbounded retries.
- Testing: Unit test with fakes; integration with TestContainers; property tests for critical logic.
- Observability: Structured JSON logs with `requestId`; redact secrets; validate config at startup.

### Angular — Modern Style Guide (v20+)
These rules apply to `ui/**` and any Angular code in this repo. They reflect current Angular guidance (v20+) using standalone APIs, Signals, and modern control flow.

- Project structure
  - Use standalone components/directives/pipes only. Do not introduce NgModules.
  - Prefer feature folders and route-driven boundaries. Lazy-load features via `loadComponent`/`loadChildren` in the router config.
- Bootstrapping and DI
  - Bootstrap with `bootstrapApplication(AppComponent, { providers: [...] })`.
  - Use provider functions, not modules: `provideRouter(routes)`, `provideHttpClient(withFetch(), withInterceptors([...]))`, `provideAnimations()` when needed.
  - Prefer `inject()` for DI inside constructors, functions, and providers. Keep constructors simple.
  - Scope services using route-level providers where appropriate for better tree-shaking and isolation.
- Routing
  - Use the functional router with `provideRouter`. Prefer functional guards/resolvers (e.g., `canActivate: [authGuard]`, `resolve: { data: myResolver }`) implemented as functions using `inject()`.
  - Use `TitleStrategy` or `withInMemoryScrolling` and other router features via provider options instead of legacy patterns.
- Templates and control flow
  - Use built-in control flow: `@if`, `@for`, `@switch` instead of `*ngIf/*ngFor/*ngSwitch`.
  - Use `@defer` blocks (with `on viewport`, `on idle`, etc.) for code-splitting and progressive rendering.
  - Always provide `track` expressions with `@for` to minimize DOM re-renders: `@for (item of items; track item.id)`.
- State management
  - Prefer Angular Signals for local UI state: `signal`, `computed`, `effect`.
  - Keep RxJS focused in services/data access. In components, convert streams via `toSignal()`/`toObservable()`.
  - Manage subscriptions with template subscriptions or `takeUntilDestroyed()`.
- Forms
  - Use strongly-typed reactive forms. Keep form state in components, not service singletons.
  - Signal-based forms: adopt only if stable in target Angular version and agreed in task.
- HttpClient
  - Use Fetch adapter with `withFetch()` unless browser/API limitation requires XHR.
  - Configure interceptors via `provideHttpClient(withInterceptors([...]))`; keep interceptors small and focused.
- Components
  - Keep components presentational; push logic to services.
  - Use `@Input()`/`@Output()` for APIs; prefer `@Model()` or `model()` for two-way binding if available.
  - Signals naturally achieve OnPush-like efficiency; avoid manual `ChangeDetectorRef` unless necessary.
- Performance, budgets & optimization
  - Honor build budgets (e.g., initial ≤ 200–250KB gzipped; async chunks ≤ 100KB).
  - Lazy-load routes and defer non-critical UI with `@defer`.
  - Optimize images: responsive `srcset/sizes`, lazy-load, use modern formats (WebP, AVIF).
  - For large lists, use virtual scrolling; always provide `track` in `@for`.
  - Use pure pipes and computed signals for derived values; avoid expensive computations in templates.
  - Monitor bundle size: `ng build --stats-json` and analyze with webpack-bundle-analyzer.
  - Defer heavy CPU work to Web Workers when appropriate.
  - Zoneless change detection: advanced/optional; enable only when explicitly requested and measured.
- Styling & CSS
  - Prefer modern CSS (Grid, Flexbox, container queries, logical properties); avoid heavy UI libs unless justified.
  - Use CSS variables for theming; keep styles scoped to components.
  - Mobile-first: write base styles for mobile, use `@media (min-width: ...)` for larger screens.
  - Use relative units (`rem`, `%`, `vw`) over fixed pixel widths.
- Mobile & accessibility (a11y)
  - **Responsive layouts**: Test at common breakpoints (320px, 768px, 1024px, 1440px).
  - **Touch targets**: Minimum 44×44px (iOS) or 48×48dp (Material) for interactive elements.
  - **Viewport**: Ensure `<meta name="viewport" content="width=device-width, initial-scale=1">` in `index.html`.
  - **Keyboard navigation**: All interactive elements accessible via Tab, Enter, Escape.
  - **Semantic HTML**: Use `<button>`, `<nav>`, `<main>`, etc.; avoid `<div>` with click handlers.
  - **ARIA**: Add labels where needed (`aria-label`, `aria-describedby`, `aria-live` for dynamic content).
  - **Screen readers**: Test with VoiceOver (iOS/macOS), TalkBack (Android).
  - **Color contrast**: Minimum 4.5:1 for text, 3:1 for large text (WCAG AA).
  - **Real device testing**: Test on actual iOS and Android devices, not just emulators.
  - **PWA (optional)**: Add only if justified; include manifest.json, service worker, icons.
- Testing
  - Test standalone components with `TestBed.configureTestingModule({ imports: [ComponentUnderTest] })` or `render` helpers; avoid TestBed modules.
  - Prefer harnesses for Angular Material components if used.
- Tooling
  - Use the Angular CLI defaults (Vite-based builder). Do not add custom webpack unless justified.
  - Keep `package.json` scripts aligned with CLI commands (`ng serve`, `ng build`, `ng test`, `ng update`).

### Context7 — Up-to-date Angular references (MANDATORY)
To ensure changes follow the most current Angular recommendations:
- Before making non-trivial UI changes, query the latest Angular docs via the Context7 tools provided in this environment.
  - Resolve library: `resolve-library-id` for “Angular”.
  - Fetch docs: `get-library-docs` for topics like standalone APIs, Signals, control flow, router, HttpClient, forms, and testing.
- Cite major decisions in PRs by linking the relevant Angular docs (versioned when possible), and note if a feature is experimental/preview.
- If guidance changed between versions, prefer the latest stable release behavior. When in doubt, ask in the PR and include the doc excerpt.

## Tooling & Commands
- Use `sbt test` for backend tests; `npm --prefix ui test` for frontend tests.
- Build/stage: `sbt stage` (runs Angular prod build then stages backend).
- E2E smoke (when present): `scripts/smoke.sh` against local or Heroku.

## Configuration & Secrets
- Do not commit secrets. Use environment variables as described in docs/requirements.md.
- If you need new config keys, update docs/requirements.md and reference them in your PR.

## Large Changes
- For schema changes, include Flyway migrations and note rollback considerations.
- For mass renames or large refactors, announce in PR and coordinate timing to reduce conflicts.

## Definition of Done (DoD) Template
- [ ] Code implements the task as described in `tasks.md`.
- [ ] All unit/integration tests pass locally and in CI.
- [ ] No unrelated changes (formatting, renames) outside task scope.
- [ ] Documentation updated:
  - [ ] Feature docs (`requirements.md`/`design.md`/`tasks.md`) linked in PR and status emojis updated.
  - [ ] README or root docs updated if behavior, config, or APIs changed.
- [ ] Logs remain structured JSON and include `requestId` (backend changes).
- [ ] Backend (if applicable):
  - [ ] Error ADTs defined and mapped to HTTP statuses consistently.
  - [ ] Database queries parameterized; indexes present; `EXPLAIN` reviewed for heavy queries.
- [ ] Frontend (if applicable):
  - [ ] Build budgets met; Lighthouse scores attached (mobile + desktop).
  - [ ] Accessibility checklist: keyboard nav, semantic HTML, ARIA labels, color contrast.
  - [ ] Tested on real devices (iOS + Android) or emulators.
  - [ ] Responsive at breakpoints: 320px, 768px, 1024px, 1440px.

## Conflict Resolution Steps
1) Rebase your branch on latest `main`: `git fetch origin && git rebase origin/main`.
2) Resolve conflicts locally; prefer keeping behavior compatible with `main` unless the task explicitly changes it.
3) Re-run tests and linters.
4) Force-push your branch if needed (`--force-with-lease`).

## File Ownership Hints
- Backend Scala: `src/main/scala/**` (server, routes, services, repos).
- DB migrations: `src/main/resources/db/migration/**`.
- Frontend Angular: `ui/**`.
- Build/infra: `build.sbt`, `project/**`, `Procfile`, `system.properties`, `.github/workflows/**`.
- Feature documentation: `docs/features/<feature-key>/**` (requirements, design, tasks).

## Communication
- Use PR comments to coordinate. Reference task IDs and link to design sections.
- If you need to pause work, leave a note in the PR with current status and next steps.
