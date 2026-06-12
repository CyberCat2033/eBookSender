# PocketBook Sender Android App

Android client for sending books to the PocketBook FTP app in this repository.

Current status: usable Compose MVP with QR/manual connection input, file/share intake, default classification, path planning, metadata previews, PocketBook catalog browsing, configurable OPDS sources, OPDS download-to-queue, DataStore settings, Room schema, and FTP atomic upload gateway.

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
- `data/ftp` - FTP gateway based on Apache Commons Net.
- `data/opds` - OPDS model/parser/repository for saved sources, browsing, and downloads.
- `data/database` - Room schema for devices, OPDS sources, and upload queue.
- `data/settings` - DataStore preferences.
- `metadata` - metadata extraction contract and filename fallback.
- `transfer` - foreground transfer service stub.

## Current MVP Behavior

- Scan QR with Google Code Scanner or paste an FTP link/IP manually.
- Pressing `Connect` with an empty FTP field launches the QR scanner.
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
- Edit `Programming` tags directly in queue items. Suggestions are loaded only from PocketBook folders under `/Programming`.
- Edit Manga series directly in queue items. Suggestions are loaded only from PocketBook folders under `/Manga`.
- Per-item category/tag/series editors are collapsed by default. Queue items show only a compact type summary until expanded.
- When multiple manga files are in the active queue, show a batch editor that applies one Manga series to all active manga items at once.
- Hide uploaded files from the active queue and show them in a collapsed `Uploaded` section.
- Skip already uploaded items when the upload button is pressed again.
- Upload execution runs through `TransferForegroundService`, so a user-started transfer continues when the app is backgrounded.
- Transfer progress and completion are shown through Android notifications. Android 13+ notification permission is requested at app start.
- Read PocketBook storage into the `Catalog` tab without downloading book contents:
  - `Books` grouped by author folders.
  - `Programming` grouped by tag folders.
- `Manga` grouped by series folders with the latest file shown.
- Catalog groups and files use natural sorting, so `2` is ordered before `10`.
- Use the separate `Web` tab to:
  - show saved OPDS sources as the primary list;
  - add a source from the `+` action;
  - remove saved OPDS sources from the source dropdown;
  - save OPDS source URLs in Room;
  - browse navigation/acquisition feeds;
  - search the current catalog through OPDS/OpenSearch when the catalog exposes `rel=search`;
  - show catalog covers when image links are available;
  - download a selected acquisition format into app cache;
  - add the downloaded file to the same upload queue as local files.
- The `Web` tab also contains native Com-X manga search/download. The embedded browser is only for login/session cookies.
- Current debug/test default OPDS source is seeded as `https://flub.flibusta.is/opds`.

CBR handling:

- CBR files are uploaded as-is.
- CBR previews use `junrar`.
- Manga archive format is detected by file signature, not only extension. A `.cbr` file that is actually ZIP is treated like CBZ.
- Current RAR support is limited to RAR4 and lower. RAR5 archives should be uploaded as-is unless another extraction backend is added later.
- Replace spaces in standard book filenames with underscores.
- Upload over FTP using temp file + rename:
  - `.Title.epub.uploading`
  - `Title.epub`
- Persist settings in DataStore:
  - root path
  - dynamic color toggle
- Launcher icon is a custom adaptive icon with foreground and monochrome layers for Android themed icons.
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

Current verified build:

```text
APK: app/build/outputs/apk/debug/app-debug.apk
Size: ~41 MB
Install: adb install -r ... -> Success
```

## Next Implementation Steps

- Persist the real upload queue through Room.
- Reintroduce Coil/OkHttp only if they add enough value. OPDS currently uses `HttpURLConnection`; cover previews also use a small local `HttpURLConnection` loader.
- Harden OPDS browsing/search against more real-world catalogs and add detail screens.
- Add legal source-adapter support for non-OPDS providers if needed.
- Implement the Manga source adapter layer for online chapter sources. The contract exists under `data/manga` and supports authenticated sources; real website adapters still need implementation.
- Add conflict UI for replace/rename/skip.

Manga adapter design notes are tracked in `../MANGA_SOURCE_ADAPTER.md`.
