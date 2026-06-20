# Changelog

All notable user-facing changes to eBookSender are documented in this file.

## [Unreleased]

### Added

- Added localized app update changelogs shown directly in the new-version dialog.
- Added automatic PocketBook server update checks when a PocketBook connects, using the shared update dialog with localized changelog support.
- Added cached changelog cleanup alongside update APK cache cleanup.

### Fixed

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

