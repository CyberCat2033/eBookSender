# AGENTS.md

## Purpose

These instructions define the required working rules for Codex in this repository. Follow them when reading, planning, editing, testing, reviewing, and committing changes.

## Engineering principles

- Follow Clean Code, KISS, DRY, and SOLID.
- Prefer simple, explicit, maintainable solutions over clever or over-engineered ones.
- Do not duplicate logic. When similar behavior appears in more than one place, extract it into a reusable component, function, service, helper, handler, or module that fits the current architecture.
- Actively seek to unify functionality. If you identify multiple similar components or behaviors (e.g., three dropdown menus with near-identical logic and slightly different animation durations for no logical design reason), propose extracting them into a single unified helper or shared component to the user and implement it upon their approval.
- Design changes with reasonable extensibility in mind, but add abstractions only when they remove real duplication or complexity.
- Preserve the existing architecture and code style unless there is a clear technical reason to improve them.
- When you see a better solution than the requested one, propose it and explain the tradeoff before applying it if the change is significant.

## Android and UI standards

- Use Kotlin and Jetpack Compose patterns already present in the project.
- UI work must follow Material Design 3 / Material You principles and use `androidx.compose.material3` components unless an existing project primitive is the better fit.
- Treat Material Design 3, Material 3, and M3 as the same design system. Prefer current Material 3 component APIs and opt into experimental Material 3 APIs only at the narrowest practical scope.
- Treat Material 3 Expressive as an M3 expansion, not a separate visual direction; adopt expressive components, motion, or typography only when they fit the app's existing utilitarian UI, accessibility, and shared motion patterns.
- Keep product-facing naming aligned with `eBookSender` across UI, docs, resources, package names, and shared code. Leave `PocketBook` naming only where it directly describes PocketBook-specific infrastructure, protocol behavior, device-profile detection, or compatibility paths.
- Route theming through `EBookSenderTheme` and `MaterialTheme` in `core:ui`; use `MaterialTheme.colorScheme`, `typography`, and `shapes` instead of hard-coded colors, text styles, elevations, or corner radii.
- Preserve Material You personalization: keep dynamic color support on Android 12+ (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) with the checked-in light/dark fallback schemes, and do not bypass the user's theme/dynamic-color settings.
- Use semantic color roles in correct pairs (for example `primary` with `onPrimary`, `primaryContainer` with `onPrimaryContainer`, `errorContainer` with `onErrorContainer`) so dynamic color and fallback schemes keep accessible contrast.
- Reuse existing theme, typography, colors, shared dialogs, status components, gesture helpers, and other `core:ui` primitives.
- Keep UI adaptive, accessible, localized, and resilient to long text.
- Do not create one-off copies of existing animations, dialogs, rows, overlays, or controls. Extract shared animation or interaction logic when reuse is evident. Proactively identify and eliminate arbitrary UI differences (e.g., mismatched animation durations or styling differences between similar controls) by proposing unified components/helpers to the user.
- Before adding haptic feedback or custom animation, check `CODEX_PROJECT_MAP.md` for the existing haptic and motion patterns.
- Use `View.performHapticIfAllowed(...)` for haptics, respect `AppSettings.enableHaptics`, and choose feedback constants consistently with existing usage.
- Reuse `AnimatedAlertDialog`, `StatusMessageHost`, gesture helpers (like `rememberDragSelectionState`, `ClickSuppressionState`, and `Modifier.pointerInputDragSelection`), lazy-list animation specs, and existing overlay/expand/progress motion patterns before creating new motion code.
- Keep press, ripple, and selection indications clipped to the same shape as the visible component. For rounded Material surfaces such as `Card`/`ElevatedCard`, prefer the component `onClick` overload or explicitly clip the indication to the surface shape; do not apply an outer rectangular `Modifier.clickable` to a rounded surface.
- Keep user-facing strings in the localization system and update bundled locales when adding or changing visible text.
- Bundled runtime locales live in `app/src/main/assets/locales/en.json` and `app/src/main/assets/locales/ru.json`; update both for every new or changed user-facing string and keep external locale loading compatible.
- Whenever adding or changing localization keys, update `docs/locales/translation-template.json` in the same change so external translators see the new keys.

## Quality, security, and performance

- Check every change for security issues: unsafe input handling, path traversal, unsafe FTP/network behavior, leaked secrets, insecure logging, and incorrect permission handling.
- Check every change for performance issues: unnecessary recomposition, blocking work on the main thread, repeated network or FTP requests, excessive allocations, unbounded caches, and inefficient database access.
- Do not leave temporary, dead, debug-only, or commented-out code.
- Handle errors explicitly and return user-facing failures through existing UI/state patterns.
- Keep public contracts stable unless the task requires changing them.

## Working process

- Start by reading the relevant files and existing analogues before editing.
- Use `rg` and `rg --files` for search.
- Use `ktlint` for Kotlin/KTS formatting when mechanical formatting is needed. Preferred command for targeted files:

  ```sh
  ~/.local/bin/ktlint -F path/to/File.kt
  ```

  If `~/.local/bin/ktlint` is missing, install the pinned CLI binary:

  ```sh
  mkdir -p ~/.local/bin
  curl -sSLo ~/.local/bin/ktlint https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint
  chmod +x ~/.local/bin/ktlint
  ```

  The repository `.editorconfig` configures ktlint for Android Studio style and Compose `@Composable` function names. Prefer formatting only files touched by the current task; use `~/.local/bin/ktlint -F "**/*.kt" "**/*.kts"` only for an explicit broad formatting task.
- For large or ambiguous tasks, create a professional plan first, split the work into clear steps, and wait for user approval before implementation.
- For small, obvious fixes, proceed directly on the `refactoring` branch while keeping the scope tight.
- Treat `README.md` as user-facing documentation only. Do not put internal Codex instructions, contributor-only workflow notes, implementation planning, or maintainer reminders there; use `AGENTS.md`, `CODEX_PROJECT_MAP.md`, or dedicated developer docs for that information.
- At the end of each task, decide whether `AGENTS.md` or `CODEX_PROJECT_MAP.md` need updates. Update them yourself when the change affects repository workflow, architecture, module layout, important paths, shared patterns, or verification commands; if no update is needed, state that in the final response.
- Use the existing module boundaries:
  - `app` wires the application, DI, navigation, metadata, transfer service, and Android entry points.
  - `core:*` modules contain shared model, domain, data, database, network, datastore, common utilities, and UI primitives.
  - `feature:*` modules contain feature-specific Compose screens, components, state, and ViewModels.
- Keep reusable domain/data/UI code out of feature modules when it is needed across features.

## Git workflow

- Check `git status --short` before making changes.
- In this environment, `.git` is read-only inside the sandbox. Run git commands that write to `.git` (for example `git add` and `git commit`) with `sandbox_permissions: "require_escalated"` immediately instead of first trying them inside the sandbox.
- Never overwrite, reset, or revert user changes unless explicitly requested.
- Do all work on a task-appropriate branch, not directly on `main`/`master`, unless the user explicitly asks otherwise.
- For refactoring, minor cleanup, and small maintenance tasks, use the dedicated `refactoring` branch. Use `refactor/<short-name>` only when a separate refactoring branch is clearly useful.
- For new features, create a dedicated branch named `feature/<short-name>`.
- For bug fixes, create a dedicated branch named `bugfix/<short-name>`.
- After finishing and verifying a feature, refactoring, or bug-fix branch, merge it back into `main`/`master` when everything is in order and the user has not asked to leave the branch separate. For new large features, wait for the user to verify the release version installed on a phone before merging.
- If only a few minor changes (e.g., 1-2 cosmetic edits, tiny bug fix) have accumulated, wait for more changes before committing (always check for accumulated changes after finishing a task).
- Commit every completed code change that modifies more than five lines, unless the user explicitly asks not to commit.
- Use professional commit messages: write a concise imperative subject that names the actual change, keep it specific enough to stand alone in history, and avoid vague subjects such as `fix`, `update`, or `changes`. For non-trivial commits, add a body that explains why the change was needed, summarizes important behavior or migration details, and calls out verification or risk when relevant.
- Keep commits logically grouped. Stage and commit only the files that belong to the current task.

## Verification

- Run the smallest relevant verification first, then broaden when the blast radius is larger.
- For Kotlin or Compose changes, prefer:

  ```sh
  GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
  ```

- In this environment, Gradle does not run reliably inside the sandbox because it fails to determine a usable wildcard IP for file locking. Run `./gradlew` commands with `sandbox_permissions: "require_escalated"` immediately instead of first retrying after a sandbox failure.

- For broader Android changes, run:

  ```sh
  GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
  ```

- When the user asks to build and install, upload, or put the app on a connected phone without specifying a build variant, install the release variant.
- When installing a release build on a connected device, prefer Gradle's install task:

  ```sh
  GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:installRelease
  ```

  Use a separate `:app:assembleRelease` plus `adb install` only when you need to inspect or archive the APK before installation.

- Run module-specific tests or lint tasks when they exist and are relevant.
- If verification cannot be run, state the exact reason and the residual risk.
- Do not call code changes complete until available verification has passed or the user explicitly accepts the risk.

## Project map

- Keep `CODEX_PROJECT_MAP.md` updated when adding, moving, or substantially changing modules, screens, services, repositories, shared UI, or verification commands.
- Do not wait for an explicit user reminder to update project guidance files. Make the update/no-update decision as part of completing every task.
- Use `CODEX_PROJECT_MAP.md` as the first navigation aid after `README.md`.
