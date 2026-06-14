# PocketBook Sender Android App

Android client for sending books to the PocketBook FTP app in this repository.

Current status: usable Compose MVP with QR/manual connection input, real FTP availability checks, file/share intake, default classification, path planning, metadata previews, PocketBook catalog browsing, configurable OPDS sources, OPDS download-to-queue, native Com-X manga search/download, DataStore settings, Room schema, and FTP atomic upload gateway.

## Project Layout

```text
android/
  app/
    src/main/java/com/cybercat/pocketbooksender/
```

The first iteration is intentionally a single Android module with package boundaries. If the app grows, split these packages into Gradle modules matching `ANDROID_APP_BRIEF.md`.

Important packages:

- `ui` - Compose app shell, navigation, screens, ViewModel.
- `model` - app models and enums.
- `domain` - file classification, FTP URL parsing, filename/path planning.
- `data/catalog` - PocketBook library database snapshot loading, parsing, and FTP folder fallback.
- `data/ftp` - FTP gateway based on Apache Commons Net.
- `data/opds` - OPDS model/parser/repository for saved sources, browsing, and downloads.
- `data/database` - Room schema for devices, OPDS sources, and upload queue.
- `data/settings` - DataStore preferences.
- `metadata` - metadata extraction contract and filename fallback.
- `data/manga` - source adapter contract, Com-X adapter, chapter history, and CBZ packing.
- `transfer` - foreground transfer service for user-started FTP uploads.

## Current MVP Behavior

- Scan QR with Google Code Scanner or paste an FTP link/IP manually.
- Pressing `Connect` with an empty FTP field launches the QR scanner.
- Pressing `Connect` with a non-empty FTP field parses the address, logs in anonymously, opens the configured root path, and runs an FTP `LIST` check before the device is considered connected.
- Accept files from Android picker.
- Accept shared files through `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.
- Classify files by extension:
  - `epub`, `fb2`, `mobi`, `azw3`, etc. -> `Books`
  - `pdf` -> `Programming`
  - `cbr`, `cbz` -> `Manga`
- Treat wrapped book names like `Book_Name.fb2.zip` by the inner book extension instead of plain `zip`.
- Extract metadata from supported files, including wrapped `fb2.zip` books.
- Show previews in the queue when extraction is available:
  - FB2 embedded cover.
  - EPUB cover image from OPF/manifest, or a fallback large image if the book has no explicit cover metadata.
  - PDF first page.
  - CBZ first image.
- Plan target paths:
  - `Books/<Author>/<Book_Title>.<ext>`
  - `Programming/<Tag>/<Title>.<ext>`
  - `Manga/<Series>/<Volume>.<ext>`
- Configure global file-name templates in Settings:
  - Books default: `{title}`
  - Programming default: `{title}`
  - Manga default: `{volume}`
  - Supported tokens: `{title}`, `{author}`, `{tag}`, `{series}`, `{volume}`.
- Edit `Programming` tags directly in queue items. Suggestions are loaded only from PocketBook folders under `/Programming`.
- Edit Manga series directly in queue items. Suggestions are loaded only from PocketBook folders under `/Manga`.
- Per-item category/tag/series editors are collapsed by default. Queue items show only a compact type summary until expanded.
- When multiple manga files are in the active queue, show a batch editor that applies one Manga series to all active manga items at once.
- Hide uploaded files from the active queue and show them in a collapsed `Uploaded` section.
- Deduplicate queued files by identity/path and skip already uploaded items when the upload button is pressed again.
- Queue cards animate when uploaded items leave the active list.
- Upload execution runs through `TransferForegroundService`, so a user-started transfer continues when the app is backgrounded.
- Transfer progress and completion are shown through Android notifications. Android 13+ notification permission is requested at app start.
- Read PocketBook storage into the `Catalog` tab without downloading book contents:
  - primary source is PocketBook's `system/explorer-3/explorer-3.db` library database, downloaded as a local snapshot together with `-wal` and `-shm`;
  - FTP folder scanning remains as fallback when the database snapshot cannot be loaded;
  - `Books` are grouped by author folder or database author metadata;
  - `Programming` is grouped by tag folders;
  - `Manga` is grouped by series folders with the latest file shown.
- Catalog entries show PocketBook database metadata when available: title, authors, read percentage, and completed state.
- Duplicate database rows for the same `book_id` are collapsed inside each Catalog area, preferring files already placed under the expected subfolder structure.
- Manga series cards surface only the last opened/read chapter progress; expanded manga file lists stay focused on file names.
- Catalog groups and files use natural sorting, so `2` is ordered before `10`.
- Catalog deletion is handled through an animated Material 3 edit mode:
  - the top-bar pencil action enters edit mode, while the trash action appears only for selected files;
  - group and file checkboxes animate in and out instead of shifting the layout abruptly;
  - expanded file rows support long-press drag selection with edge autoscroll, matching manga chapter selection;
  - dragging back shrinks the live selection range and restores files outside the range to their pre-gesture state;
  - deletion requires confirmation and is limited to supported files under `Books`, `Programming`, and `Manga`.
- Use the separate `Web` tab to:
  - show saved OPDS sources as the primary list;
  - add a source from the `+` action;
  - remove saved OPDS sources from the source dropdown;
  - save OPDS source URLs in Room;
  - browse navigation/acquisition feeds;
  - search the current catalog through OPDS/OpenSearch when the catalog exposes `rel=search`;
  - show catalog covers when image links are available;
  - reuse Android `HttpResponseCache` for `HttpURLConnection` responses when source servers allow caching;
  - cache remote covers as downsampled JPEG previews on disk;
  - download a selected acquisition format into app cache;
  - add the downloaded file to the same upload queue as local files;
  - tolerate duplicate OPDS entry ids from catalogs such as Flibusta by generating unique list keys locally.
- The `Web` tab also contains native Com-X manga search/download:
  - the embedded browser is only for login/session cookies and closes automatically after successful login cookie detection;
  - Com-X login state requires DLE auth cookies instead of treating any guest/session cookie as authenticated;
  - series pages are fetched once per open and cached briefly in memory to avoid duplicate network + HTML parse work;
  - search results are cached briefly in memory for fast repeat/back flows;
  - favorite and subscribed manga series are stored in Room;
  - subscribed manga can be checked for new chapters, opening the first updated series and selecting new chapters automatically;
  - selected series details show the latest downloaded chapter and best-effort latest read chapter from the PocketBook catalog when available, with the static download button removed;
  - a floating action button (FAB) is displayed at the bottom of the screen when at least one manga chapter is selected and no download is active;
  - active manga download progress is shown as a persistent bottom overlay so it stays visible while scrolling through chapter lists;
  - search results and chapter rows are clickable across the full card/row, not only on the trailing button/checkbox;
  - chapter rows support long-press drag selection with edge autoscroll;
  - dragging back shrinks the live selection range and restores chapters outside the range to their pre-gesture state;
  - selected chapters are downloaded and packed into per-chapter CBZ files;
  - downloaded chapter history is stored in Room and used to mark already handled chapters.
- Current debug/test default OPDS source is seeded as `https://flub.flibusta.is/opds`.

CBR handling:

- CBR files are uploaded as-is.
- CBR previews use `junrar`.
- Manga archive format is detected by file signature, not only extension. A `.cbr` file that is actually ZIP is treated like CBZ.
- Current RAR support is limited to RAR4 and lower. RAR5 archives should be uploaded as-is unless another extraction backend is added later.
- CBR-to-CBZ conversion is intentionally not exposed anymore; conversion was removed as unnecessary for the current PocketBook flow.
- Replace spaces in standard book filenames with underscores.
- Upload over FTP using temp file + rename:
  - `.Title.epub.uploading`
  - `Title.epub`
- Persist settings in DataStore:
  - root path
  - default Programming tag
  - default Manga series
  - Books/Programming/Manga filename templates
  - dynamic color toggle
- Launcher icon is a custom adaptive icon with foreground and monochrome layers for Android themed icons, and Android 12+ launcher colors are backed by system Material colors.
- Settings screen is scrollable on short screens.

## Build Requirements

Expected setup:

- Android Studio.
- Android SDK Platform 36.
- Android SDK Build Tools 34+.
- JDK 17+.
- Gradle 8.13 through the checked-in wrapper.

Open the `android/` directory in Android Studio, let it sync Gradle dependencies, then build/run the `app` configuration.

Once a Gradle wrapper is added, the expected command is:

```sh
cd android
./gradlew :app:assembleDebug
```

In this workspace the project is configured with:

```properties
sdk.dir=/home/cybercat/Android/Sdk
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk
```

For sandboxed Codex runs, use a writable Gradle home:

```sh
cd android
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Install on an attached phone:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing is read from ignored local configuration or environment, not from tracked Gradle files. Add these values to `local.properties`, pass them as Gradle properties, or export matching environment variables:

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<store password>
RELEASE_KEY_ALIAS=<key alias>
RELEASE_KEY_PASSWORD=<key password>
```

`local.properties` and `*.keystore` are ignored by git. Keep real passwords and keystore files out of commits.

## Verified Builds & Verification

Latest local verification on 2026-06-14:

```sh
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
# Install result: Success
# Release APK size: 2.3 MB
```

Both debug and optimized/signed release versions are verified:

### Debug Build
```sh
./gradlew :app:assembleDebug
# APK size: ~43 MB
# Install: adb install -r app/build/outputs/apk/debug/app-debug.apk -> Success
```

### Release Build (Optimized & Signed)
Builds with R8 full minification and resource shrinking enabled, signed with a valid release keystore configured through ignored release signing properties, and optimized with Proguard rules:

```sh
./gradlew :app:assembleRelease
# APK size: ~2.3 MB (significant reduction from 43 MB via R8/proguard optimization!)
# Install: adb install -r app/build/outputs/apk/release/app-release.apk -> Success
```

## Next Implementation Steps

- Persist the real upload queue through Room.
- Reintroduce Coil/OkHttp only if they add enough value. OPDS currently uses `HttpURLConnection` plus Android `HttpResponseCache`; OPDS catalog pages also have a short in-memory cache; cover previews use a custom sub-sampled, throttled (max 3 downloads), memory/disk-cached JPEG loader.
- Harden OPDS browsing/search against more real-world catalogs and add detail screens.
- Harden the Com-X adapter against markup changes, expired sessions, archive-download fallback failures, and large chapter batches.
- Add more manga source adapters behind the existing `MangaSourceAdapter` contract if needed.
- Add conflict UI for replace/rename/skip.

Manga adapter design notes are tracked in `../MANGA_SOURCE_ADAPTER.md`.
