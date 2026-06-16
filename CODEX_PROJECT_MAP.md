# CODEX_PROJECT_MAP.md

## Purpose

This file helps Codex navigate the project quickly and edit files safely. Keep it current when the architecture, module layout, major features, or verification commands change.

## Project overview

PocketBook Sender is a Kotlin Android app built with Gradle, Jetpack Compose, Material 3, Hilt, Room, DataStore, FTP integration, OPDS browsing, foreground manga/download transfer support, persisted upload queue state, and runtime localization.

## Gradle modules

- `:app` - Android application entry point, DI setup, navigation shell, metadata extraction, transfer and manga foreground services, persisted queue implementation, app resources, manifest, and launcher assets.
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
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AdaptiveLayout.kt` - shared adaptive width class and screen padding tokens for Compact, Medium, and Expanded layouts.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AnimatedAlertDialog.kt` - shared animated dialog pattern.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/AppStrings.kt` - string access model.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/LocalizationManager.kt` - runtime localization loading.
- `app/src/main/java/com/cybercat/pocketbooksender/ui/PocketBookSenderApp.kt` - Compose app shell and navigation integration.
- `app/src/main/java/com/cybercat/pocketbooksender/PocketBookSenderApplication.kt` - Hilt application entry point, HTTP cache setup, and process lifecycle hooks for app-wide resource control.
- `core/data/src/main/java/com/cybercat/pocketbooksender/transfer/ConnectionManager.kt` - shared PocketBook connection state and foreground-only keep-alive monitoring.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/TransferForegroundService.kt` - foreground FTP upload service.
- `app/src/main/java/com/cybercat/pocketbooksender/manga/MangaDownloadForegroundService.kt` - foreground manga chapter download service that keeps downloads running while the app is backgrounded and adds completed chapters to the upload queue.
- `app/src/main/java/com/cybercat/pocketbooksender/power/ScopedWakeLock.kt` - small non-reference-counted wake-lock helper for strictly scoped foreground transfer/download CPU wake windows.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/UploadQueueManagerImpl.kt` - upload queue manager; persists queue snapshots in app storage and checks restored file/URI access.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaDownloadCoordinator.kt` - app/feature boundary for foreground manga download requests and progress/completion events.
- `app/src/main/java/com/cybercat/pocketbooksender/di/MangaSourceModule.kt` - collects every installed manga source adapter into a Hilt set of `HtmlMangaSourceAdapter` using multibindings.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/MangalibMangaAdapter.kt` - MangaLib source adapter implementing `HtmlMangaSourceAdapter` using cdnlibs API.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/network/NetworkStateChecker.kt` - Android connectivity helper used to avoid manga retry loops while no active internet-capable network is available.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaSelectionKeys.kt` - shared stable manga selection keys used by subscription update UI and background download completion.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaArchiveHelper.kt` - packaging tool for creating CBZ/ZIP files from downloaded manga page images.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/FtpErrorMapper.kt` - mapper class translating FTP connection errors and URL parsing failures to localized user-facing strings.

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

## Haptic feedback

- Central helper: `core/ui/src/main/java/com/cybercat/pocketbooksender/util/HapticHelper.kt`.
- Use `View.performHapticIfAllowed(context, enableHaptics, feedbackConstant, ignoreDnd)` instead of calling `performHapticFeedback` directly.
- `performHapticIfAllowed` respects the app setting, Do Not Disturb, and silent mode. Use `ignoreDnd = true` only for continuous drag-selection feedback that already follows this pattern.
- User setting source: `AppSettings.enableHaptics` in `core/model`, persisted by `SettingsRepository` with the `enable_haptics` DataStore key.
- Settings UI toggle: `feature/settings/.../SettingsScreen.kt`; toggling haptics intentionally uses `enableHaptics = true` so the user feels the action even when enabling feedback.
- Current feedback constants:
  - `VIRTUAL_KEY` - normal taps, toggles, source selection, expand/collapse, picker actions, text clear actions, and segmented choices.
  - `CONFIRM` - destructive confirmations after dialog confirmation, upload/download actions, save/commit actions, and successful primary actions.
  - `REJECT` - remove/delete/disconnect style actions.
  - `LONG_PRESS` - entering selection or stronger maintenance/destructive actions.
  - `CLOCK_TICK` - repeated range changes during drag selection in Catalog and Manga.
- Feature coverage:
  - `app/.../PocketBookSenderApp.kt` passes haptic setting into Catalog, OPDS, Manga, and Settings and uses haptics for manga navigation/FAB actions.
  - `feature/catalog` uses haptics for edit mode, file/group selection, delete flow, expand/collapse, long-press drag selection, and drag-selection ticks.
  - `feature/manga` uses haptics for search/browser/actions, chapter selection, saved/subscribed series controls, subscription updates, and drag-selection ticks.
  - `feature/opds` uses haptics for source picker, credentials/source actions, search, feed navigation, and download buttons.
  - `feature/settings` uses haptics for switches, language/theme choices, save/confirm actions, and cache clearing.
  - `feature/transfer` uses haptics for connect inputs, add/upload, remove, batch rename, queue item expansion, uploaded section expansion, and category/suggestion choices.

## Custom animation map

- Shared dialogs: `core/ui/.../AnimatedAlertDialog.kt`.
  - Material 3 `BasicAlertDialog` wrapped in `AnimatedVisibility`.
  - Enter: `scaleIn(initialScale = 0.96f)` plus `fadeIn`, using `FastOutSlowInEasing`, 260 ms scale and 220 ms fade.
  - Exit: `scaleOut(targetScale = 0.98f)` plus `fadeOut`, 220 ms.
  - Use `LocalDismissDialog.current` from dialog buttons so exit animation runs before state removal.
  - Use `LocalDismissDialogAfter.current` when an action would repaint large UI, such as applying a language after the dialog closes.
- Shared status messages: `core/ui/.../StatusComponents.kt`.
  - `StatusMessageHost` preserves the last non-empty text and shows/hides it with fade, vertical expand/shrink, and a small vertical slide.
- App shell: `app/src/main/java/com/cybercat/pocketbooksender/ui/PocketBookSenderApp.kt`.
  - `NavHost` screen transitions use fade-in plus subtle `scaleIn(0.96f)` with 200 ms duration and 80 ms delay; exits fade out in 80 ms.
  - Bottom navigation reselection uses `LazyListState.animateScrollToTop()`.
  - Manga download FAB enters/exits with fade plus vertical slide from the bottom.
- Gesture helpers: `core/ui/src/main/java/com/cybercat/pocketbooksender/util/GestureHelpers.kt`.
  - `rememberDragSelectionState` encapsulates state tracking, drag distance calculation, and edge autoscrolling.
  - `detectDragGesturesAfterQuickLongPress` starts drag selection after a 300 ms quick long press and consumes active drag events before lazy-list scrolling.
  - `calculateAutoScrollDelta` drives edge autoscroll during drag selection; Catalog and Manga pair it with `LONG_PRESS` start feedback and `CLOCK_TICK` range-change feedback.
- Catalog motion: `feature/catalog/.../CatalogScreen.kt` and `CatalogComponents.kt`.
  - Top app bar title/actions use `AnimatedContent`; edit-mode close icon uses fade plus horizontal expand/shrink.
  - `SelectionSlot` animates checkbox slot width, alpha, and scale over `SelectionMotionDurationMillis = 220`.
  - Group expansion uses vertical expand/shrink plus fade; duration is `minOf(750, 250 + fileCount * 35)`.
  - Expand chevrons rotate with a medium spring.
  - File deletion rows shrink upward and fade out with `RemovalMotionDurationMillis = 260`.
  - Long-press drag selection uses edge autoscroll and haptic ticks when selection state changes.
- Transfer queue motion: `feature/transfer/.../SendScreen.kt`, `TransferComponents.kt`, and `UploadItemRow.kt`.
  - Queue rows use `Modifier.animateItem` with `QueueFadeInSpec`, `QueueFadeOutSpec`, and `QueuePlacementSpec`.
  - `AnimatedRemovalItem` handles single-item removal and clear-queue removal with horizontal slide-out, vertical shrink, fade, and staggered clear delays.
  - Active transfer overlay enters/exits with fade plus vertical slide from the bottom.
  - Active upload progress values are held separately from the persisted upload queue so high-frequency progress ticks do not rewrite the whole queue list or JSON snapshot.
  - Connection panel animates icon tint over 220 ms, disconnect button alpha over 160 ms, and FTP input visibility with spring expand/shrink plus fade.
  - Upload item details use spring expand/shrink plus fade; uploaded section uses progressive tween expand/shrink plus fade based on the number of items; chevrons rotate with a medium spring.
  - Upload progress height uses `animateDpAsState`; item and overall progress bars use low-stiffness no-bounce spring `animateFloatAsState`.
- Manga motion: `feature/manga/.../MangaPane.kt` and `MangaComponents.kt`.
  - Chapter and search-result lists use `Modifier.animateItem()`.
  - Opening a selected series scrolls the list with `animateScrollToItem`.
  - Active manga download overlay enters/exits with fade plus vertical slide from the bottom.
  - Manga download progress uses low-stiffness no-bounce spring `animateFloatAsState`.
  - Subscription update groups rotate chevrons with a medium spring and expand/collapse chapter lists with spring expand/shrink plus fade.
  - Chapter drag selection uses the shared quick-long-press gesture, edge autoscroll, and haptic ticks.
- OPDS motion: `feature/opds/.../OpdsScreen.kt` and `OpdsComponents.kt`.
  - Add-source and credentials dialogs reuse `AnimatedAlertDialog`.
  - OPDS entry rows use `Modifier.animateItem()` for list placement changes.
- Settings motion: `feature/settings/.../SettingsScreen.kt`.
  - Editable setting trailing action uses `AnimatedContent` with 120 ms fade-in and 90 ms fade-out between save icon and spinner.
  - Folder rename warning and language dialogs reuse `AnimatedAlertDialog`.
  - Maintenance section uses `animateContentSize` with a medium-low spring.
  - Maintenance status messages use `AnimatedContent`: expand/fade in when present, shrink/fade out when cleared, and auto-clear after 3 seconds.

## Verification commands

Use the checked-in Gradle wrapper from the Android project root.

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
```

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

For installing release builds on a connected device, prefer Gradle's install task:

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:installRelease
```

Run narrower module tasks when available and relevant. If Gradle needs network access for dependency resolution, request approval instead of bypassing the sandbox.

## Git notes

- The repository root is `android/`.
- Check `git status --short` before and after edits.
- Existing user changes may be present; do not include unrelated files in commits.
- For large new features, work on `feature/<short-name>` and merge only after successful verification and user agreement.
