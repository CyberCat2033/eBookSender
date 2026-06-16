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
- UI work must follow Material You / Material Design 3 principles.
- Reuse existing theme, typography, colors, shared dialogs, status components, gesture helpers, and other `core:ui` primitives.
- Keep UI adaptive, accessible, localized, and resilient to long text.
- Do not create one-off copies of existing animations, dialogs, rows, overlays, or controls. Extract shared animation or interaction logic when reuse is evident. Proactively identify and eliminate arbitrary UI differences (e.g., mismatched animation durations or styling differences between similar controls) by proposing unified components/helpers to the user.
- Before adding haptic feedback or custom animation, check `CODEX_PROJECT_MAP.md` for the existing haptic and motion patterns.
- Use `View.performHapticIfAllowed(...)` for haptics, respect `AppSettings.enableHaptics`, and choose feedback constants consistently with existing usage.
- Reuse `AnimatedAlertDialog`, `StatusMessageHost`, gesture helpers (like `rememberDragSelectionState`, `ClickSuppressionState`, and `Modifier.pointerInputDragSelection`), lazy-list animation specs, and existing overlay/expand/progress motion patterns before creating new motion code.
- Keep press, ripple, and selection indications clipped to the same shape as the visible component. For rounded Material surfaces such as `Card`/`ElevatedCard`, prefer the component `onClick` overload or explicitly clip the indication to the surface shape; do not apply an outer rectangular `Modifier.clickable` to a rounded surface.
- Keep user-facing strings in the localization system and update bundled locales when adding or changing visible text.
- Bundled runtime locales live in `app/src/main/assets/locales/en.json` and `app/src/main/assets/locales/ru.json`; update both for every new or changed user-facing string and keep external locale loading compatible.

## Quality, security, and performance

- Check every change for security issues: unsafe input handling, path traversal, unsafe FTP/network behavior, leaked secrets, insecure logging, and incorrect permission handling.
- Check every change for performance issues: unnecessary recomposition, blocking work on the main thread, repeated network or FTP requests, excessive allocations, unbounded caches, and inefficient database access.
- Do not leave temporary, dead, debug-only, or commented-out code.
- Handle errors explicitly and return user-facing failures through existing UI/state patterns.
- Keep public contracts stable unless the task requires changing them.

## Working process

- Start by reading the relevant files and existing analogues before editing.
- Use `rg` and `rg --files` for search.
- For large or ambiguous tasks, create a professional plan first, split the work into clear steps, and wait for user approval before implementation.
- For small, obvious fixes, proceed directly on the `refactoring` branch while keeping the scope tight.
- Use the existing module boundaries:
  - `app` wires the application, DI, navigation, metadata, transfer service, and Android entry points.
  - `core:*` modules contain shared model, domain, data, database, network, datastore, common utilities, and UI primitives.
  - `feature:*` modules contain feature-specific Compose screens, components, state, and ViewModels.
- Keep reusable domain/data/UI code out of feature modules when it is needed across features.

## Git workflow

- Check `git status --short` before making changes.
- Never overwrite, reset, or revert user changes unless explicitly requested.
- For new large features, create a dedicated branch named `feature/<short-name>`. After implementing the changes and having the user verify them (specifically on the release version installed on a phone), merge the branch into `main`/`master` if everything is in order.
- For small tasks, minor refactoring, and code cleanup, work on a dedicated `refactoring` branch (or `refactor/<short-name>`) to avoid polluting the git history.
- If only a few minor changes (e.g., 1-2 cosmetic edits, tiny bug fix) have accumulated, wait for more changes before committing (always check for accumulated changes after finishing a task).
- If a change is significant, commit it with a descriptive and clear commit message (avoid short 1-2 word messages).
- Keep commits logically grouped. Stage and commit only the files that belong to the current task.

## Verification

- Run the smallest relevant verification first, then broaden when the blast radius is larger.
- For Kotlin or Compose changes, prefer:

  ```sh
  GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
  ```

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
- Use `CODEX_PROJECT_MAP.md` as the first navigation aid after `README.md`.
