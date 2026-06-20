# Changelog

All notable user-facing changes to eBookSender are documented in this file.

## [Unreleased]

### Added

- Added localized app update changelogs shown directly in the new-version dialog.
- Added automatic PocketBook server update checks when a PocketBook connects, using the shared update dialog with localized changelog support.
- Added cached changelog cleanup alongside update APK cache cleanup.

### Fixed

- Disabled app-data backup and blocked app-wide cleartext HTTP by default; PocketBook local control HTTP remains limited to private/local device addresses.
- Hide the dynamic color toggle on devices running Android 11 and lower since they do not support dynamic coloring.
- Fixed naming-rule token hints so books, documents, and manga show only the tokens that apply to that file type.
- Fixed duplicate loading indicators while deleting selected catalog books.
- Fixed transfer and manga download progress notifications so they appear while the app is in the background and disappear again when the app is opened.
- Fixed stale FTP connection state by periodically checking the connected device and clearing the catalog/status when the server is no longer reachable.
