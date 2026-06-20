# Changelog

All notable user-facing changes to eBookSender are documented in this file.

## [0.11.5] - 2026-06-20

### Added

- Added localized app update changelogs shown directly in the new-version dialog.
- Added automatic PocketBook server update checks when a PocketBook connects, using the shared update dialog with localized changelog support.
- Added cached changelog cleanup alongside update APK cache cleanup.
- Added bilingual English/Russian changelog notes to GitHub Release pages for the current app version.

### Fixed

- Fixed app update changelog publication so clients that checked the release before upgrading can display the 0.11.5 notes instead of an empty "Unreleased" section.
- Fixed the settings update button so the silent app-open update check no longer leaves it disabled with "Checking for updates" after startup.
- Removed chapter title from manga download notifications for cleaner text.
- Optimized manga chapter downloads by streaming pages to disk instead of keeping all page bytes in RAM, significantly reducing memory pressure and preventing Out of Memory crashes.
- Clean up partially downloaded and empty `.cbz` manga files if archiving fails or is canceled.
- Fixed a potential race condition in catalog loading and refreshed view updates.
- Improved retries for manga page downloads by switching to an exponential backoff delay strategy.
- Optimized thread-safety and performance of device connection manager by replacing blocking locks with non-blocking StateFlow updates.
- Disabled app-data backup and blocked app-wide cleartext HTTP by default; PocketBook local control HTTP remains limited to private/local device addresses.
- Hide the dynamic color toggle on devices running Android 11 and lower since they do not support dynamic coloring.
- Fixed naming-rule token hints so books, documents, and manga show only the tokens that apply to that file type.
- Fixed duplicate loading indicators while deleting selected catalog books.
- Hid the misleading "Not started" reading status for generic FTP catalog entries where reading progress is unavailable.
- Fixed transfer and manga download progress notifications so they stay visible for the whole background batch instead of disappearing between chapters or items, and still disappear when the app is opened.
- Fixed stale FTP connection state by periodically checking the connected device and clearing the catalog/status when the server is no longer reachable.
- Fixed PocketBook server updates so the app no longer reports success until the updated server actually reports its new version.
- Fixed PocketBook server update staging so the uploaded launcher keeps the final `pb-ftp.app` name instead of a version-prefixed file name.
