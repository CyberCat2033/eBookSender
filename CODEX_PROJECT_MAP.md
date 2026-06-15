# CODEX_PROJECT_MAP.md

## Purpose

This file helps Codex navigate the project quickly and edit files safely. Keep it current when the architecture, module layout, major features, or verification commands change.

## Project overview

PocketBook Sender is a Kotlin Android app built with Gradle, Jetpack Compose, Material 3, Hilt, Room, DataStore, FTP integration, OPDS browsing, manga download support, and runtime localization.

## Gradle modules

- `:app` - Android application entry point, DI setup, navigation shell, metadata extraction, transfer service, queue implementation, app resources, manifest, and launcher assets.
- `:core:model` - shared app models such as upload items, settings, device catalog, categories, and PocketBook device data.
- `:core:common` - common utilities, constants, formatting helpers, coroutine helpers, result helpers, and small shared primitives.
- `:core:domain` - pure domain logic: FTP URL parsing, file classification, path planning, filename sanitizing, book formats, and natural sorting.
- `:core:database` - Room database, DAOs, entities, and type converters.
- `:core:datastore` - DataStore-backed settings repository.
- `:core:network` - OPDS parsing/models/download formats and manga source adapters such as Com-X.
- `:core:data` - repositories and data orchestration for catalog, FTP, OPDS, manga, transfer, connection management, and PocketBook control.
- `:core:ui` - shared Compose UI, Material 3 theme, animated dialogs, status components, remote covers, bitmap cache, localization, gestures, and haptics.
- `:feature:catalog` - PocketBook catalog screen, state, components, selection, deletion, and catalog ViewModel.
- `:feature:manga` - manga pane, state, components, selection behavior, and manga ViewModel.
- `:feature:opds` - Web/OPDS screen, OPDS components, state, navigation, and ViewModel.
- `:feature:settings` - settings screen, state, dialogs, language and folder/template settings, and ViewModel.
- `:feature:transfer` - send queue screen, upload item rows, transfer components, state, and ViewModel.

## Important paths

- `settings.gradle.kts` - authoritative module list.
- `gradle/libs.versions.toml` - dependency and plugin versions.
- `build.gradle.kts` - root Gradle plugin declarations.
- `app/build.gradle.kts` - app configuration and dependencies.
- `app/src/main/AndroidManifest.xml` - Android components, permissions, intent filters, and services.
- `app/src/main/assets/locales/en.json` - bundled English localization.
- `app/src/main/assets/locales/ru.json` - bundled Russian localization.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/theme/Theme.kt` - Material 3 app theme.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AnimatedAlertDialog.kt` - shared animated dialog pattern.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/AppStrings.kt` - string access model.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/LocalizationManager.kt` - runtime localization loading.
- `app/src/main/java/com/cybercat/pocketbooksender/ui/PocketBookSenderApp.kt` - Compose app shell and navigation integration.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/TransferForegroundService.kt` - foreground FTP upload service.

## Search and edit workflow

- Use `rg --files` to find files and `rg "<term>"` to find symbols or text.
- Before creating new code, search for existing analogues in `core:*`, `feature:*`, and `app`.
- Put cross-feature UI in `core:ui`, cross-feature business logic in `core:domain` or `core:data`, and feature-only Compose/state code in the relevant `feature:*` module.
- Keep Android platform services and app wiring in `:app` unless a reusable interface belongs in `core:data`.
- Update localization JSON files whenever visible text changes.
- Check all call sites when changing shared models, repositories, domain helpers, or UI primitives.

## Reuse rules

- Reuse `AnimatedAlertDialog` for Material 3 dialogs with shared fade/scale behavior.
- Reuse `core:ui` gesture and haptic helpers for drag selection and touch feedback.
- Reuse domain helpers for classification, filename/path planning, natural sorting, and sanitization.
- Reuse repositories and transfer abstractions instead of making direct FTP, Room, DataStore, or network calls from UI code.
- Extract repeated Compose rows, dialogs, overlays, and animations when a second real use case appears.

## Verification commands

Use the checked-in Gradle wrapper from the Android project root.

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
```

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Run narrower module tasks when available and relevant. If Gradle needs network access for dependency resolution, request approval instead of bypassing the sandbox.

## Git notes

- The repository root is `android/`.
- Check `git status --short` before and after edits.
- Existing user changes may be present; do not include unrelated files in commits.
- For large new features, work on `feature/<short-name>` and merge only after successful verification and user agreement.
