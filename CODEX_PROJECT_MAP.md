# CODEX_PROJECT_MAP.md

## Purpose

This file helps Codex navigate the project quickly and edit files safely. Keep it current when the architecture, module layout, major features, or verification commands change.

## Project overview

PocketBook Sender is a Kotlin Android app built with Gradle, Jetpack Compose, Material 3, Hilt, Room, DataStore, FTP integration, automatic remote-device profile detection, OPDS browsing, foreground manga/download transfer support, persisted upload queue state, and runtime localization. PocketBook remains the primary optimized device profile; generic FTP devices are supported through folder scanning and no-op library refresh.

## Gradle modules

- `:app` - Android application entry point, DI setup, navigation shell, metadata extraction, transfer and manga foreground services, persisted queue implementation, app resources, manifest, and launcher assets.
- `:core:model` - shared app models such as upload items, settings, device catalog, categories, `RemoteDevice`, and `DeviceProfile`.
- `:core:common` - common utilities, constants, formatting helpers, coroutine helpers, result helpers, and small shared primitives.
- `:core:domain` - pure domain logic: FTP URL parsing, file classification, path planning, filename sanitizing, book formats, and natural sorting.
- `:core:database` - Room database, DAOs, entities, and type converters.
- `:core:datastore` - DataStore-backed settings repository.
- `:core:network` - OPDS parsing/models/download formats and manga source adapters such as Com-X.
- `:core:data` - repositories and data orchestration for catalog, FTP, OPDS, manga, transfer, connection management, automatic device profile detection, profile-aware library refresh, and PocketBook-specific control.
- `:core:ui` - shared Compose UI, Material 3 theme, animated dialogs, status components, remote covers, bitmap cache, localization, gestures, and haptics.
- `:feature:catalog` - device catalog screen, state, components (split into `CatalogComponents.kt`, `CatalogConstants.kt`, `CatalogExtensions.kt`), selection, deletion, and catalog ViewModel.
- `:feature:manga` - manga pane, state, components (split into `MangaComponents.kt`, `MangaSearchComponents.kt`, `MangaBrowserComponents.kt`, `MangaSubscriptionUpdatesDialog.kt`), selection behavior, and manga ViewModel.
- `:feature:opds` - Web/OPDS screen, OPDS components (split into `OpdsComponents.kt`, `OpdsDialogs.kt`, `OpdsEntryItems.kt`), state, navigation, auth/download controllers, and ViewModel.
- `:feature:settings` - settings screen, state, components and dialogs (split into `SettingsScreen.kt`, `SettingsComponents.kt`, `SettingsDialogs.kt`, `StorageSettingsSection.kt`, `NamingSettingsSection.kt`, `InterfaceSettingsSection.kt`, `MaintenanceSettingsSection.kt`), language and folder/template settings, and ViewModel.
- `:feature:transfer` - send queue screen, upload item rows, transfer components (split into `TransferComponents.kt`, `TransferDialogs.kt`), state, and ViewModel.

## Important paths

- `settings.gradle.kts` - authoritative module list.
- `.editorconfig` - ktlint formatting settings for Kotlin/KTS files, including Android Studio style and Compose `@Composable` naming compatibility.
- `gradle/libs.versions.toml` - dependency and plugin versions.
- `build.gradle.kts` - root Gradle plugin declarations.
- `app/build.gradle.kts` - app configuration and dependencies.
- `core/model/src/main/java/com/cybercat/pocketbooksender/model/AppSettings.kt` - persisted user settings model, shared FTP mount/relative-root path normalization, folder/template preferences, theme/haptics, localization, and local-device VPN-bypass behavior.
- `core/model/src/main/java/com/cybercat/pocketbooksender/model/RemoteDevice.kt` - connected remote FTP device model, detected `DeviceProfile`, profile-derived capabilities, working-root path, and display FTP URL.
- `core/common/src/main/java/com/cybercat/pocketbooksender/network/LocalNetworkBypassUnavailableException.kt` - shared warning error for Android/VPN policies that block optional local-route bypass.
- `core/model/src/main/java/com/cybercat/pocketbooksender/model/UploadItemEntity.kt` - serializable app-local upload queue persistence entity and mappers; runtime-only fields such as upload progress are not persisted, and queue cover previews live in a shared disk cache keyed by upload item id.
- `app/src/main/AndroidManifest.xml` - Android components, permissions, intent filters, and services.
- `app/src/main/assets/locales/en.json` - bundled English localization.
- `app/src/main/assets/locales/ru.json` - bundled Russian localization.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/theme/Theme.kt` - Material 3 app theme.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/theme/Motion.kt` - Material 3 animation curve tokens (e.g. `EmphasizedEasing`).
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AdaptiveLayout.kt` - shared adaptive width class and screen padding tokens for Compact, Medium, and Expanded layouts.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AppTextFields.kt` - shared Material 3 outlined text fields that preserve selection state, support single-line horizontal scrolling, and expose simple string state to feature screens; use for app text inputs before reaching for raw `OutlinedTextField`.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/AnimatedAlertDialog.kt` - shared animated dialog pattern.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/AppStrings.kt` - string access model.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/localization/LocalizationManager.kt` - runtime localization loading.
- `app/src/main/java/com/cybercat/pocketbooksender/ui/PocketBookSenderApp.kt` - Compose app shell and navigation integration.
- `app/src/main/java/com/cybercat/pocketbooksender/PocketBookSenderApplication.kt` - Hilt application entry point, HTTP cache setup, and process lifecycle hooks for app-wide resource control.
- `app/src/main/java/com/cybercat/pocketbooksender/di/AppCoroutineModule.kt` - Hilt module that provides the shared application-lifetime coroutine scope and exception handler for singleton background managers and repositories.
- `app/src/main/java/com/cybercat/pocketbooksender/di/MangaDataModule.kt` - Hilt binding module that exposes `MangaRepository` as the shared `MangaSeriesPageLoader` port for manga use cases.
- `app/src/main/java/com/cybercat/pocketbooksender/lifecycle/AppVisibilityTracker.kt` - process importance visibility gate used by foreground services to suppress completion notifications while the UI is visible.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/LocalMetadataExtractor.kt` - local metadata extractor router and remaining FB2/EPUB/MOBI metadata parsing for queued files.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/DocxMetadataParser.kt` - DOCX core-properties parser for basic document metadata without preview extraction.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/PdfMetadataParser.kt` - Android `PdfRenderer` first-page preview extraction.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/MangaArchiveMetadataParser.kt` - CBZ/CBR preview extraction using ZIP/RAR format detection and bounded image decoding.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/MetadataPreviewDecoder.kt` - shared bounded bitmap preview decoder for local metadata parsers.
- `app/src/main/java/com/cybercat/pocketbooksender/metadata/MobiMetadataParser.kt` - bounded PalmDB/MOBI/EXTH parser for MOBI/AZW3 title, author, publisher/year/language, and cover image records.
- `core/data/src/main/java/com/cybercat/pocketbooksender/transfer/ConnectionManager.kt` - shared remote-device connection state.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/device/DeviceProfileDetector.kt` - automatic profile detector; probes PocketBook library DB visibility after FTP connection and falls back to `GenericFtp`.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/device/DeviceLibraryRefresher.kt` - profile-aware library refresh boundary; delegates PocketBook rescan and returns success for generic FTP devices.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/DeviceCatalogRepository.kt` - profile-aware catalog repository; coordinates catalog state, deletion, PocketBook DB source, and FTP folder source.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/DeviceCatalogSource.kt` - catalog source interface used by profile-specific and generic catalog loaders.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/PocketBookCatalogSource.kt` - PocketBook catalog source that builds `DeviceCatalog` from the `explorer-3.db` snapshot.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/ftp/CommonsNetFtpGateway.kt` - Apache Commons Net FTP gateway; opens the FTP mount root from the link, then creates/opens the Settings relative root where the app hierarchy lives. Atomic uploads write to a temporary `.uploading` file and clean it up when coroutine cancellation interrupts a transfer. All list/download/delete/rename paths go through shared FTP relative-path validation, and suspicious entry names from the server are skipped instead of being exposed to the catalog/UI.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/ftp/FtpPathSecurity.kt` - shared FTP path-hardening helpers; validates relative FTP paths, rejects `.`/`..` traversal, rejects absolute paths and control characters, and safely combines parent/child FTP entry paths.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/pocketbook/PocketBookLibraryPaths.kt` - shared PocketBook library database paths and storage prefix used by profile detection and database reading.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/PocketBookDatabaseReader.kt` - PocketBook `explorer-3.db` snapshot reader; downloads the SQLite database files from the FTP mount root, opens the local copy read-only, and maps cursor rows under the Settings relative root to catalog file records.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/CatalogTreeBuilder.kt` - pure catalog tree builder for database records; filters supported file types, deduplicates PocketBook book records, maps metadata to `CatalogFile`, and groups Books/Documents/Manga with natural sorting.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/catalog/CatalogFolderScanner.kt` - generic FTP folder catalog source and PocketBook fallback when the SQLite database is unavailable or empty.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/DownloadOpdsEntriesUseCase.kt` - OPDS multi-entry download interactor; selects supported acquisitions, coordinates parallel publication downloads, reports each completed file through a callback so cancellation keeps already downloaded files, and returns downloaded files plus per-entry failure counts to the OPDS download controller.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/SearchOpdsCatalogUseCase.kt` - OPDS search interactor; orchestrates search URL building, OpenSearch template loading, author-index fallback loading, and merged result delivery for the OPDS ViewModel.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsSearchSupport.kt` - pure OPDS search helpers for template expansion, Flibusta author-search URL derivation, author-entry filtering, and merged search-catalog assembly.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsDownloadFileSupport.kt` - pure OPDS download helpers for URL normalization, download file-name resolution, MIME-based extension mapping, and collision-safe cache file naming.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/MatchOpdsAuthSourceUseCase.kt` - OPDS auth-source resolver; returns the saved `OpdsSource` whose host matches the URL that raised `OpdsAuthenticationRequiredException`, so the ViewModel can open the credentials dialog for the correct source.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsSecureCredentialsStore.kt` - Android Keystore-backed OPDS credential vault; encrypts saved OPDS usernames/passwords with AES/GCM in app-private preferences and replaces the previous plaintext Room storage.
- `feature/opds/src/main/java/com/cybercat/pocketbooksender/feature/opds/OpdsAuthController.kt` - feature-layer OPDS credentials dialog controller; saves credentials and retries the protected catalog URL after successful update.
- `feature/opds/src/main/java/com/cybercat/pocketbooksender/feature/opds/OpdsDownloadController.kt` - feature-layer OPDS download state controller; owns active download job, progress updates, cancellation, queue insertion, and auth/error routing for single and multi-entry downloads.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsCredentialsProviderImpl.kt` - OPDS credentials provider that matches saved source credentials by request host and reads the decrypted credentials from the secure OPDS vault.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsHttpClient.kt` - OPDS `HttpURLConnection` boundary; applies Accept/User-Agent headers, Basic auth from URLs or saved source credentials, manual redirects, timeouts, and HTTP status validation.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/opds/OpdsUrlResolver.kt` - shared OPDS relative URL resolver used by catalog links, acquisitions, redirects, and search template resolution.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/TransferForegroundService.kt` - foreground FTP upload service; coordinates service lifecycle, wake lock, transfer requests, user cancellation, profile-aware library refresh, and OPDS/manga app-cache cleanup after successful uploads.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/TransferNotificationManager.kt` - notification channel, progress, cancellation, and minimized-app completion notification helper for foreground FTP uploads.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/DownloadCacheManager.kt` - shared app-local OPDS/manga download-cache cleanup helper used by transfer completion and queue removal.
- `app/src/main/java/com/cybercat/pocketbooksender/manga/MangaDownloadForegroundService.kt` - foreground manga chapter download service that keeps downloads running while the app is backgrounded, supports user cancellation, and adds fully completed chapters to the upload queue.
- `app/src/main/java/com/cybercat/pocketbooksender/power/ScopedWakeLock.kt` - small non-reference-counted wake-lock helper for strictly scoped foreground transfer/download CPU wake windows.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/UploadQueueManagerImpl.kt` - upload queue manager; coordinates queue state, persistence, serialized local metadata loading to cap batch-add memory usage, writes extracted previews into the shared disk cache instead of retaining `Bitmap`s in queue state, replans upload paths, skips deduplication for status-only queue mutations, and delegates app-local file/cache access.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/LocalFileResolver.kt` - Android `ContentResolver` boundary for queued upload source display names, file sizes, persistable read permissions, and source readability checks.
- `app/src/main/java/com/cybercat/pocketbooksender/transfer/QueueStorageRepository.kt` - app-local upload queue JSON/file persistence boundary using `UploadItemEntity` and kotlinx.serialization.
- `core/common/src/main/java/com/cybercat/pocketbooksender/util/UploadPreviewCache.kt` - shared app-local JPEG preview cache for queued upload covers; saves sampled previews under app files storage, reloads them with bounded decode, and cleans up orphaned entries by upload item id.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaDownloadCoordinator.kt` - app/feature boundary for foreground manga download requests and progress/completion events.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaChapterDownloader.kt` - manga chapter download pipeline; owns archive/page fallback, retry/timeouts, concurrency limits, network availability checks, CBZ creation, download history item construction, and partial completed-batch reporting on cancellation.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaDownloadModels.kt` - shared manga download request/result/progress models used by the downloader, foreground service, coordinator, and feature UI.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/CheckMangaSubscriptionsUseCase.kt` - manga subscription-check interactor; reads subscribed series and download history, opens saved series through `MangaSeriesPageLoader`, marks successful checks, and derives new chapters.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaSourceRegistry.kt` - sorted source adapter registry that exposes `MangaSourceSummary` data and resolves source adapters by id for repository/downloader callers.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaSeriesRecoveryHeuristics.kt` - pure manga-series recovery helpers for ranking moved-series search candidates and normalizing saved-series identity keys.
- `app/src/main/java/com/cybercat/pocketbooksender/di/MangaSourceModule.kt` - collects every installed manga source adapter into a Hilt set of `HtmlMangaSourceAdapter` using multibindings.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/network/NetworkStateChecker.kt` - Android connectivity helper used to avoid manga retry loops while no active internet-capable network is available.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/network/LocalDeviceNetworkProvider.kt` - Android network route helper for optional VPN bypass on local FTP and device library-refresh requests, including a bind probe before using a direct Wi-Fi/Ethernet route.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaSelectionKeys.kt` - shared stable manga selection keys used by subscription update UI and background download completion.
- `core/data/src/main/java/com/cybercat/pocketbooksender/data/manga/MangaArchiveHelper.kt` - packaging tool for creating CBZ/ZIP files from downloaded manga page images.
- `core/data/src/main/java/com/cybercat/pocketbooksender/di/ApplicationScope.kt` - qualifier for the shared application-lifetime coroutine scope injected into singleton managers and repositories.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxMangaAdapter.kt` - Com-X source adapter; owns the `HtmlMangaSourceAdapter` contract and delegates HTTP/session work plus HTML parsing.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxMangaHttpClient.kt` - Com-X HTTP facade for text fetching and image downloads; delegates session, connection, Guard, and archive responsibilities to focused helpers.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxHttpConnectionFactory.kt` - shared Com-X `HttpURLConnection` setup for headers, timeouts, user agent, referer, and cookies.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxMangaSessionManager.kt` - Com-X WebView cookie/session boundary, authenticated-session detection, cookie capture, and auth-expiration checks.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxGuardChallengeClient.kt` - Com-X Guard challenge submission helper used when fetched HTML reports a challenge page.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxArchiveDownloader.kt` - Com-X chapter archive authorization, streaming download, progress reporting, and archive type detection.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxHttpResponseHelpers.kt` - shared Com-X HTTP response/error snippet helpers.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxHtmlParser.kt` - Com-X HTML parser facade; keeps guard detection while delegating search, series-page, and reader-page extraction.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxSeriesPageParser.kt` - Com-X series page parser for series details and chapter-list extraction from `window.__DATA__` or reader links.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxSearchParser.kt` - Com-X search parser for readed blocks, poster links, and JSON-LD search-result fallbacks.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxReaderPageParser.kt` - Com-X reader-page parser for chapter image extraction from `window.__DATA__`, nested script JSON objects, image tags, and `srcset`.
- `core/network/src/main/java/com/cybercat/pocketbooksender/data/manga/ComxParsingHelpers.kt` - shared Com-X parser helpers for URL ownership/normalization, title cleanup, JSON field extraction, structured script JSON extraction, and image URL handling.
- `core/network/src/test/java/com/cybercat/pocketbooksender/data/manga/ComxParserTest.kt` - JVM unit tests for resilient Com-X series and reader-page parsing without network access.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/FtpErrorMapper.kt` - mapper class translating FTP connection errors and URL parsing failures to localized user-facing strings.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/BitmapCache.kt` - shared preview bitmap cache with disk persistence for remote covers and expiring in-memory entries reused by remote and queued-upload cover rendering.
- `core/ui/src/main/java/com/cybercat/pocketbooksender/ui/UploadPreviewCover.kt` - shared lazy Compose cover loader for queued upload items; reads disk-cached local previews by upload item id and only materializes visible cover bitmaps into bounded memory cache.
- `core/domain/src/main/java/com/cybercat/pocketbooksender/domain/MangaTitleParser.kt` - pure domain parser that derives manga series/volume hints from local file names before metadata extraction.

## Search and edit workflow

- Use `rg --files` to find files and `rg "<term>"` to find symbols or text.
- Before creating new code, search for existing analogues in `core:*`, `feature:*`, and `app`.
- Put cross-feature UI in `core:ui`, cross-feature business logic in `core:domain` or `core:data`, and feature-only Compose/state code in the relevant `feature:*` module.
- Keep Android platform services and app wiring in `:app` unless a reusable interface belongs in `core:data`.
- Update localization JSON files whenever visible text changes.
- Format touched Kotlin/KTS files with `~/.local/bin/ktlint -F <files>` when mechanical formatting is needed. If the CLI is missing, install ktlint 1.8.0 into `~/.local/bin/ktlint` using the command in `AGENTS.md`.
- Check all call sites when changing shared models, repositories, domain helpers, or UI primitives.
- At task completion, decide whether `AGENTS.md` or this project map need updates. Update them yourself when guidance, navigation, architecture, shared patterns, important paths, or verification commands changed; if not, mention that no project-guidance update was needed.

## Reuse rules

- Reuse `AnimatedAlertDialog` for Material 3 dialogs with shared fade/scale behavior.
- Reuse `AppOutlinedTextField` for Material 3 text input so cursor/selection behavior, single-line horizontal scrolling, and placeholders stay consistent across features.
- Reuse `core:ui` gesture and haptic helpers for drag selection and touch feedback.
- Reuse domain helpers for classification, filename/path planning, natural sorting, and sanitization.
- Reuse repositories and transfer abstractions instead of making direct FTP, Room, DataStore, or network calls from UI code.
- Extract repeated Compose rows, dialogs, overlays, and animations when a second real use case appears.

## Haptic feedback

- Central helper: `core/ui/src/main/java/com/cybercat/pocketbooksender/util/HapticHelper.kt`.
- Prefer `View.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.*)` over raw `HapticFeedbackConstants` so tap/confirm/reject/long-press semantics stay consistent across features.
- `AppHapticFeedback` currently centralizes `Press`, `Confirm`, `Reject`, `LongPress`, `DragStart`, and `DragTick`.
- `performHapticIfAllowed` respects the app setting, Do Not Disturb, and silent mode. `DragStart` and `DragTick` are the only built-in variants that bypass DND/silent checks because they are reserved for continuous drag-selection feedback.
- User setting source: `AppSettings.enableHaptics` in `core/model`, persisted by `SettingsRepository` with the `enable_haptics` DataStore key.
- Settings UI toggle: `feature/settings/.../SettingsScreen.kt`; toggling haptics intentionally uses `enableHaptics = true` so the user feels the action even when enabling feedback.
- Current semantic mappings:
  - `Press` - normal taps, toggles, source selection, expand/collapse, picker actions, text clear actions, and segmented choices.
  - `Confirm` - destructive confirmations after dialog confirmation, upload/download actions, save/commit actions, and successful primary actions.
  - `Reject` - remove/delete/disconnect style actions.
  - `LongPress` - stronger maintenance/destructive actions that are not drag-selection.
  - `DragStart` - entering drag-selection mode.
  - `DragTick` - repeated range changes during drag selection in Catalog and Manga.
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
  - `NavHost` screen transitions use fade-in plus subtle `scaleIn(0.96f)` over 200 ms without a delayed start; exits fade out in 80 ms.
  - `NavigationSuiteScaffold` keeps the default Material 3 adaptive layout except landscape orientation, where the app uses a custom full-height `NavigationRail` so primary navigation stays on the left and each destination receives equal vertical hit area on phones.
  - Primary navigation uses a route-specific click gate to ignore duplicate taps to the same destination during transition startup; reselection uses `LazyListState.animateScrollToTop()`.
  - Manga download FAB enters/exits with fade plus vertical slide from the bottom.
- Gesture helpers: `core/ui/src/main/java/com/cybercat/pocketbooksender/util/GestureHelpers.kt`.
  - `rememberDragSelectionState` encapsulates state tracking, drag distance calculation, and edge autoscrolling.
  - `detectDragGesturesAfterQuickLongPress` starts drag selection after a 300 ms quick long press and consumes active drag events before lazy-list scrolling.
  - `calculateAutoScrollDelta` drives edge autoscroll during drag selection; Catalog and Manga pair it with `LONG_PRESS` start feedback and `CLOCK_TICK` range-change feedback.
  - `ClickSuppressionState` / `rememberClickSuppressionState` manages temporary click suppression on items to prevent false click triggers when releasing a drag selection gesture.
  - `Modifier.pointerInputDragSelection` is a unified modifier that hooks up drag-selection start, drag, end/cancel, and click suppression.
- Catalog motion: `feature/catalog/.../CatalogScreen.kt`, `CatalogComponents.kt`, and `CatalogConstants.kt`.
  - Top app bar title/actions use `AnimatedContent`; edit-mode close icon uses fade plus horizontal expand/shrink.
  - `SelectionSlot` animates checkbox slot width, alpha, and scale over `SelectionMotionDurationMillis = 220`.
  - Group expansion uses vertical expand/shrink plus fade; duration is `minOf(750, 250 + fileCount * 35)`.
  - Expand chevrons rotate with a medium spring.
  - File deletion rows shrink upward and fade out with `RemovalMotionDurationMillis = 260`.
  - Long-press drag selection uses edge autoscroll and haptic ticks when selection state changes.
- Transfer queue motion: `feature/transfer/.../SendScreen.kt`, `TransferComponents.kt`, `TransferDialogs.kt`, and `UploadItemRow.kt`.
  - Queue rows use `Modifier.animateItem` with `QueueFadeInSpec`, `QueueFadeOutSpec`, and `QueuePlacementSpec`.
  - `AnimatedRemovalItem` handles single-item removal and clear-queue removal with horizontal slide-out, vertical shrink, fade, and staggered clear delays.
  - Active transfer overlay enters/exits with fade plus vertical slide from the bottom and exposes a localized cancel action that stops the remaining upload queue.
  - Foreground FTP uploads run sequentially; transfer runtime state keeps the active item id plus current progress separate from the persisted upload queue so progress ticks do not rewrite the whole queue list or JSON snapshot, while status-only queue mutations skip full-list deduplication and cover-cache cleanup.
  - Queue cover thumbnails load lazily from `UploadPreviewCache` through `UploadPreviewCover`, so visible rows can display previews without retaining every extracted `Bitmap` inside queue state or ViewModel state.
  - Connection panel animates icon tint over 220 ms, disconnect button alpha over 160 ms, and FTP input visibility with spring expand/shrink plus fade.
  - Upload item details use spring expand/shrink plus fade; uploaded section uses progressive tween expand/shrink plus fade based on the number of items; chevrons rotate with a medium spring.
  - Upload progress height uses `animateDpAsState`; item and overall progress bars use low-stiffness no-bounce spring `animateFloatAsState`.
- Manga motion: `feature/manga/.../MangaPane.kt`, `MangaComponents.kt`, `MangaSearchComponents.kt`, `MangaBrowserComponents.kt`, and `MangaSubscriptionUpdatesDialog.kt`.
  - Chapter and search-result lists use `Modifier.animateItem()`.
  - Opening a selected series scrolls the list with `animateScrollToItem`.
  - Active manga download overlay enters/exits with fade plus vertical slide from the bottom and includes a localized Material 3 cancel action.
  - Manga download progress uses low-stiffness no-bounce spring `animateFloatAsState`.
  - Subscription update groups rotate chevrons with a medium spring and expand/collapse chapter lists with spring expand/shrink plus fade.
  - Chapter drag selection and subscription updates dialog chapter selection use the shared quick-long-press gesture, edge autoscroll, and haptic ticks via `pointerInputDragSelection`.
- OPDS motion: `feature/opds/.../OpdsScreen.kt`, `OpdsComponents.kt`, `OpdsDialogs.kt`, and `OpdsEntryItems.kt`.
  - Add-source and credentials dialogs reuse `AnimatedAlertDialog`.
  - Switching between OPDS and Manga in the Web tab uses horizontal slide plus fade through `AnimatedContent`.
  - OPDS catalog loads and feed navigation fade/slide the incoming list content while preserving the single `LazyListState`.
  - Active OPDS download overlay enters/exits with fade plus vertical slide from the bottom and includes a localized Material 3 cancel action; completed files are queued before cancellation clears the active state.
  - OPDS entry rows use `Modifier.animateItem()` for list placement changes.
  - OPDS feed navigation uses `OpdsNavigation.kt` helpers; hierarchy back and page navigation are separate. `OpdsPagingState` drives the bottom page bar, including local previous-page history when a catalog page does not expose `previous`/`prev`.
- Settings motion: `feature/settings/.../SettingsScreen.kt`, `SettingsComponents.kt`, and `SettingsDialogs.kt`.
  - Editable setting trailing action uses `AnimatedContent` with 120 ms fade-in and 90 ms fade-out between save icon and spinner.
  - Settings IME padding is applied inside the scroll chain so focused inputs can move above the keyboard without a fixed spacer over the screen.
  - Focused settings fields use `SettingsFocusedFieldScrollHost` to animate the shared `ScrollState` so the focused input settles near the upper third of the settings viewport; the focus scroll waits briefly for IME and naming-token layout changes to stabilize, then uses the shared `EmphasizedEasing` curve for a single scroll animation.
  - Folder rename warning and language dialogs reuse `AnimatedAlertDialog`.
  - Maintenance section uses `animateContentSize` with a medium-low spring.
  - Maintenance status messages use `AnimatedContent`: expand/fade in when present, shrink/fade out when cleared, and auto-clear after 3 seconds.

## Verification commands

Use the checked-in Gradle wrapper from the Android project root.

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
```

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :core:network:testDebugUnitTest
```

```sh
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

For Kotlin/KTS formatting of touched files:

```sh
~/.local/bin/ktlint -F path/to/File.kt
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
