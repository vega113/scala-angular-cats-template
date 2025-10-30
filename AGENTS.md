# Collaboration Guide for AI/Dev Agents

This repository may be worked on by multiple agents in parallel. Follow these rules to avoid stepping on each other’s work and to keep changes safe and reviewable.

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
  - Checklist for the task’s Definition of Done (from docs/tasks.md).
  - Any migrations or operational steps.
  - Screenshots or logs where relevant.
- Mark PRs as draft until tests pass and you’ve self-reviewed.

## Task Status Updates
- On opening a PR, set the task’s status emoji in `docs/tasks.md` to 🟢 (In progress).
- When the PR is ready for review, update to 🟣 (Ready for review).
- After merge to `main`, update the status to ✅ (Done).
- If blocked by another task or external dependency, set 🔴 (Blocked) and add a short note in the PR.
- Keep estimates as-is; optionally add actuals in the PR body for retrospective.

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
- [ ] Code implements the task as described.
- [ ] All unit/integration tests pass locally and in CI.
- [ ] No unrelated changes included.
- [ ] Documentation updated (README or docs/*) if behavior or config changed.
- [ ] Logs remain JSON and include requestId.

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

## Communication
- Use PR comments to coordinate. Reference task IDs and link to design sections.
- If you need to pause work, leave a note in the PR with current status and next steps.
