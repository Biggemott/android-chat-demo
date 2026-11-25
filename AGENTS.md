# Chat Demo — engineering instructions

## Project
A standalone native Android chat demo for two fixed users. Kotlin, Jetpack Compose, Material 3, Hilt, Room, coroutines/Flow, reactive MVVM with unidirectional data flow. Single Gradle app module; use clear package boundaries. Keep the implementation concise and production-minded. Do not add speculative functionality or infrastructure.

## Task workflow
- Read the current task and the local project brief under `_workspace/` if provided. The task defines scope and acceptance criteria; inspect existing code before choosing implementation details.
- Implement only the authorized task. Do not start subsequent tasks or revise agreed product behavior without approval. Choose idiomatic implementation details yourself, document consequential trade-offs, and surface conflicts before proceeding.
- Use installed Superpowers `test-driven-development` for behavioral changes and `verification-before-completion` before completion. Use `systematic-debugging` when necessary. The supplied task specs replace repeated brainstorming; use `writing-plans` only when complexity warrants it. Do not use full `subagent-driven-development` by default.
- Request one separate specification/code-quality review per completed implementation task using available review capabilities; resolve material findings and reverify. Prefer a separate reviewer context, not a new agent for each small step. If a separate reviewer is unavailable, report that limitation explicitly; do not represent self-review as independent review. Use `receiving-code-review` where applicable.
- For behavior, write tests first, run and observe the expected failure, implement the minimum correct behavior, then refactor if useful. Do not manufacture RED evidence or add artificial tests for configuration/data-class boilerplate. Record commands and real outcomes.
- Verify relevant tests, build and lint as appropriate. Report skipped/failed checks and why. Never claim a check ran or passed without evidence. Do not disable tests or weaken acceptance criteria to pass.
- Preserve unrelated user changes. Never force-push, publish, or push without explicit authorization. Do not commit credentials, local settings, private requirements, internal AI transcripts, or supplied third-party reference assets.
- Keep working notes, plans and task reports in ignored `_workspace/` (if a tool requires another output location, ensure it is also excluded before creating/committing). Public documentation describes the standalone project; do not include third-party assignment/company names, original reference text, logos, or private correspondence.

## Development quality

- Maintain zero Android Lint errors and zero unjustified warnings. Resolve actionable warnings introduced or encountered during implementation. Document the technical reason for every warning intentionally retained or suppressed.
- Remove unused imports, dead code, unused resources, and unnecessary dependencies. Prefer stable, non-deprecated APIs.
- Keep dependencies stable and mutually compatible. Review and document deliberate version pins, and keep unrelated upgrades out of feature work.
- Update README.md after every task that introduces meaningful functionality, architecture decisions, or limitations. Record intentional trade-offs and limitations as the implementation progresses.
- Include these standards in subsequent code reviews.

## Architecture guidelines
- Room is the single source of truth for persisted messages; no independent mutable message list in presentation state.
- Use domain models independent of Android/Room; use Kotlin inline value classes for meaningful domain IDs, mapping to Room-compatible primitive types at persistence boundaries when appropriate.
- Use constructor injection with Hilt and lifecycle-aware coroutine/Flow collection. Avoid layers or interfaces with no meaningful responsibility. Keep time and random behavior controllable in tests.
- Use stable, mutually compatible dependencies and version catalogs; no unnecessary network SDKs, authentication, or background services. Preserve generated Room schema JSON in Git.
- UI is English by default; use resource strings and prepare for Arabic/RTL, custom light/dark themes and accessibility. No need to implement future UI work during data tasks.

## Git conventions
Use Conventional Commits: `type(scope): imperative description` (scope optional when not useful). Types: `feat`, `fix`, `test`, `refactor`, `chore`, `build`, `ci`, `docs`, `perf`. English, imperative mood, no trailing period; one coherent change per commit. Avoid generic messages, internal task numbers and AI-tool names in commit subjects.

Keep the integration branch working. For selected core behavior (message grouping, time sectioning, reply debounce), preserve meaningful RED/GREEN commits on a feature branch and integrate without squashing only once green and reviewed. RED commits may fail the specific expected test; do not push broken branch tips or create artificial RED commits after implementation. For other changes, commit a coherent tested result; no mandatory refactor-only commit.

Examples:
- `chore: initialize Android Compose project`
- `build: configure Hilt and Room`
- `feat(data): implement relational chat storage`
- `test(chat): define message grouping boundary cases`
- `feat(chat): implement message grouping`
- `docs: describe architecture and limitations`

## Completion report (local)
Record scope implemented, key design decisions, files changed, RED/GREEN evidence where applicable, build/test/lint commands and results, review findings/resolutions or reviewer availability, commit IDs, and remaining blockers. Store under `_workspace/reports/`; keep public commits free of internal task material.
