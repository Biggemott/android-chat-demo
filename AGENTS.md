# Chat Demo — engineering instructions

## Project
A standalone native Android chat demo for two fixed users. Kotlin, Jetpack Compose, Material 3, Hilt, Room, coroutines/Flow, reactive MVVM with unidirectional data flow. Single Gradle app module; use clear package boundaries. Keep the implementation concise and production-minded. Do not add speculative functionality or infrastructure.

## Task workflow
- Read the current task and the local project brief under `_workspace/` if provided. The task defines scope and acceptance criteria; inspect existing code before choosing implementation details.
- Implement only the authorized task. Do not start subsequent tasks or revise agreed product behavior without approval. Choose idiomatic implementation details yourself, document consequential trade-offs, and surface conflicts before proceeding.
- Use Superpowers `test-driven-development` for behavioral changes. Observe the expected failure before implementing, then record the actual commands and outcomes. Keep tests focused on behavior. Use `verification-before-completion` before reporting completion and `systematic-debugging` when needed. The task specs supply the design. Use `writing-plans` when complexity warrants it and reserve full `subagent-driven-development` for tasks that call for it.
- Request one separate specification and code-quality review per implementation task. Resolve material findings and reverify. Report any limit on reviewer availability. Use `receiving-code-review` where applicable.
- Run focused tests and static checks during implementation. Before completing a task, run `verifyProject` and use the [device verification workflow](docs/engineering.md#device-checks-and-ci) to run isolated connected tests and validate fresh results separately on the minimum and target APIs where available. Run screenshot verification when supported. Reuse the fast gate in CI. Record results and explain skipped or failed checks. Do not disable tests or weaken acceptance criteria to pass.
- Preserve unrelated user changes. Never force-push, publish, or push without explicit authorization. Do not commit credentials, local settings, private requirements, internal AI transcripts, or supplied third-party reference assets.
- When a synced Android Studio and Android CLI are available, follow the [local IDE inspection workflow](docs/engineering.md#optional-local-ide-inspections) for changed Kotlin sources, tests, Gradle scripts, and XML resources. Record findings, retained warnings, and any files whose results could not be verified.
- Keep working notes, plans and task reports in ignored `_workspace/` (if a tool requires another output location, ensure it is also excluded before creating/committing). Public documentation describes the standalone project; do not include third-party assignment/company names, original reference text, logos, or private correspondence.

## Development quality

- Maintain zero Android Lint errors and zero unjustified warnings. Resolve actionable warnings introduced or encountered during implementation. Document the technical reason for every warning intentionally retained or suppressed.
- Remove unused imports, dead code, unused resources, and unnecessary dependencies. Prefer stable, non-deprecated APIs.
- Keep dependencies stable and mutually compatible. Review and document deliberate version pins, and keep unrelated upgrades out of feature work.
- Update README.md after every task that introduces meaningful functionality, architecture decisions, or limitations. Record intentional trade-offs and limitations as the implementation progresses.

## Architecture guidelines
- Room is the single source of truth for persisted messages; no independent mutable message list in presentation state.
- Use domain models independent of Android/Room; use Kotlin inline value classes for meaningful domain IDs, mapping to Room-compatible primitive types at persistence boundaries when appropriate.
- Use constructor injection with Hilt and lifecycle-aware coroutine/Flow collection. Avoid layers or interfaces with no meaningful responsibility. Keep time and random behavior controllable in tests.
- Manage dependency versions in the catalog. Preserve generated Room schema JSON in Git. Keep the app offline and account-free. Avoid unnecessary background services.
- Preserve English fallback, Arabic/RTL layout, custom light/dark themes, and accessibility. Keep UI text in resources.
- Colocate private Compose previews with the components they exercise so they appear in Android Studio's Design panel for those files. Share deterministic fixtures with screenshot tests and keep the actual UI tooling dependency debug-only.

## Git conventions
Use Conventional Commits: `type(scope): imperative description` (scope optional when not useful). Types: `feat`, `fix`, `test`, `refactor`, `chore`, `build`, `ci`, `docs`, `perf`. English, imperative mood, no trailing period; one coherent change per commit. Avoid generic messages, internal task numbers and AI-tool names in commit subjects.

Keep the integration branch working. For selected core behavior (message grouping, time sectioning, reply debounce), preserve meaningful RED/GREEN commits on a feature branch and integrate without squashing only once green and reviewed. RED commits may fail the specific expected test; do not push broken branch tips or create artificial RED commits after implementation. For other changes, commit a coherent tested result; no mandatory refactor-only commit.

## Completion report (local)
Record scope implemented, key design decisions, files changed, RED/GREEN evidence where applicable, build/test/lint commands and results, review findings/resolutions or reviewer availability, commit IDs, and remaining blockers. Store under `_workspace/reports/`; keep public commits free of internal task material.
