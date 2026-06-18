<div align="center">

# eBookSender

### Android-приложение для отправки книг, документов и манги на электронную читалку по FTP

[![Версия](https://img.shields.io/github/v/release/CyberCat2033/eBookSender?label=%D0%92%D0%B5%D1%80%D1%81%D0%B8%D1%8F&sort=semver)](../../releases/latest)
[![Платформа](https://img.shields.io/badge/%D0%9F%D0%BB%D0%B0%D1%82%D1%84%D0%BE%D1%80%D0%BC%D0%B0-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-00B0FF.svg)](https://developer.android.com/tools/releases/platforms#8.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-34A853.svg)](https://developer.android.com/about/versions/15)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/compose)
[![Лицензия](https://img.shields.io/badge/%D0%9B%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-GPL--2.0-blue.svg)](LICENSE)

</div>

---

**eBookSender** помогает отправлять файлы с Android-телефона на электронную читалку без кабеля, облачных сервисов и промежуточного компьютера. Приложение подключается к FTP-серверу читалки по локальной сети, раскладывает файлы по папкам, показывает очередь отправки и умеет работать с локальными файлами, OPDS-каталогами и мангой из Com-X.

Приложение рассчитано не только на разработчиков: если на читалке есть FTP-сервер, достаточно установить APK, подключить телефон и читалку к одной Wi-Fi сети и указать FTP-адрес в приложении.

---

## Возможности

### Отправка файлов на читалку

- Отправка книг, документов и манги по FTP внутри локальной сети.
- Поддерживаемые форматы: `epub`, `fb2`, `mobi`, `azw3`, `txt`, `rtf`, `pdf`, `djvu`, `doc`, `docx`, `cbz`, `cbr`.
- Добавление файлов из приложения или через системное меню Android **«Поделиться»**.
- Поддержка одиночной и массовой отправки файлов.
- Автоматическое определение PocketBook по служебной базе `explorer-3.db`; остальные FTP-серверы используются как универсальные устройства.
- Автоматическая раскладка по папкам:
  - книги: `Books/<Author>/<Title>.<ext>`;
  - документы: `Documents/<Tag>/<Title>.<ext>`;
  - манга: `Manga/<Series>/<Series>_<Volume>.<ext>`.
- Названия папок и шаблоны имён можно изменить в настройках.
- Доступные токены шаблонов: `{title}`, `{author}`, `{tag}`, `{series}`, `{volume}`, `{year}`, `{language}`, `{index}`, `{publisher}`, `{ext}`, `{original}`.
- Передача выполняется атомарно: файл сначала загружается как временный `.uploading`, а затем переименовывается после успешной отправки.
- Для CBZ приложение обновляет `ComicInfo.xml` и приводит структуру архива к единому виду перед отправкой.

### Метаданные и обложки

- Извлечение названия, автора, серии, года, языка, издателя и других доступных метаданных из поддерживаемых форматов.
- Превью обложек в очереди отправки для EPUB, FB2, MOBI/AZW3, PDF и архивов манги.
- Ручная корректировка категории, серии и других данных перед отправкой.
- Массовое изменение серии для нескольких глав манги в очереди.

### Каталог устройства

- Просмотр содержимого подключенной читалки прямо из приложения.
- Группировка книг, документов и манги по структуре папок устройства.
- Для PocketBook приложение дополнительно читает библиотечную базу устройства и показывает данные каталога с учетом этой базы.
- Удаление файлов и папок с подтверждением.
- Режим редактирования с мультивыделением, drag-выделением и автоскроллом.

### OPDS-каталоги

- Подключение к онлайн-библиотекам по протоколу OPDS.
- Поиск, навигация по каталогам, обложки и скачивание книг.
- Поддержка нескольких OPDS-источников.
- HTTP Basic Auth для защищенных каталогов.
- Хранение учетных данных через Android Keystore с AES/GCM.
- Скачанные книги попадают в общую очередь отправки.

### Манга Com-X

- Поиск серий и глав в источнике Com-X.
- Открытие страниц серий, выбор глав и загрузка в формате CBZ.
- Три режима авторизации: спросить при входе, WebView или нативная форма.
- Избранные серии и подписки хранятся локально.
- Проверка обновлений по подпискам с выбором новых глав для скачивания.
- История загрузок: уже обработанные главы помечаются автоматически.

### Интерфейс и настройки

- Интерфейс на Jetpack Compose и Material 3.
- Светлая, темная и системная темы.
- Dynamic Color на Android 12+.
- Адаптивная навигация для телефонов и планшетов.
- Русский и английский языки из коробки.
- Пользовательские переводы через JSON: можно взять [шаблон](docs/locales/translation-template.json) или [английский пример](app/src/main/assets/locales/en.json), затем положить готовый `.json` в `eBookSender/locales/` на устройстве.
- Тактильная обратная связь с возможностью отключения.
- Фоновая отправка файлов и фоновая загрузка манги через foreground service.
- Опциональный обход VPN для локальных FTP-подключений.
- Подключение по QR-коду или ручной ввод FTP-адреса.

---

## Скриншоты

<div align="center">

| Отправка | Каталог устройства | Web / OPDS | Настройки |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/01_send.png" width="220" alt="Экран отправки"> | <img src="docs/screenshots/02_catalog.png" width="220" alt="Каталог устройства"> | <img src="docs/screenshots/03_web.png" width="220" alt="Веб-каталог OPDS"> | <img src="docs/screenshots/04_settings.png" width="220" alt="Настройки"> |

</div>

---

## Как это работает

```text
Локальный файл / «Поделиться»        OPDS / Com-X
              |                         |
              v                         v
        Очередь отправки <--- метаданные, обложки, шаблоны
              |
              v
        Планирование пути: Books / Documents / Manga
              |
              v
        FTP-загрузка в фоне
              |
              v
        Файл появляется на читалке
```

---

## Установка для пользователей

### 1. Скачайте APK

1. Откройте страницу [Releases](../../releases).
2. Найдите последний релиз.
3. Скачайте файл приложения с расширением `.apk`, например:

   ```text
   eBookSender-vX.Y.Z.apk
   ```

### 2. Разрешите установку APK

Android может предупредить, что приложение устанавливается не из Google Play. Разрешите установку для браузера или файлового менеджера, из которого открываете APK.

Обычно это делается через системную подсказку при открытии файла. Если подсказка не появилась, найдите в настройках Android раздел установки неизвестных приложений и разрешите установку для нужного приложения.

### 3. Установите приложение

1. Откройте скачанный `.apk`.
2. Нажмите **Установить**.
3. После установки нажмите **Открыть**.

### 4. Подключите читалку

1. Подключите телефон и читалку к одной Wi-Fi сети.
2. Включите FTP-сервер на читалке.
3. Откройте eBookSender.
4. Отсканируйте QR-код с FTP-адресом или введите адрес вручную.
5. Нажмите **Подключить**.

После подключения можно добавлять файлы в очередь, проверять путь отправки и запускать передачу.

---

## Частые сценарии

### Отправить книгу из файлового менеджера

1. Откройте файл книги на телефоне.
2. Нажмите **Поделиться**.
3. Выберите **eBookSender**.
4. Проверьте файл в очереди и нажмите отправку.

### Скачать книгу из OPDS и отправить на читалку

1. Откройте раздел Web / OPDS.
2. Выберите или добавьте OPDS-источник.
3. Найдите книгу.
4. Скачайте ее в очередь.
5. Отправьте очередь на подключенную читалку.

### Скачать главы манги

1. Откройте раздел манги.
2. Найдите серию в Com-X.
3. Выберите главы.
4. Скачайте их в CBZ.
5. Отправьте полученные файлы на читалку.

---

## Сборка для разработчиков

### Требования

| Компонент | Версия |
| --- | --- |
| Android Studio | актуальная версия с поддержкой AGP 8.13 |
| Android SDK Platform | 36 |
| Android SDK Build Tools | 34+ |
| JDK | 17+ |
| Gradle | 8.13 через wrapper |

### Клонирование

```sh
git clone https://github.com/<ваш-форк>/PocketBook-SFTP.git
cd PocketBook-SFTP/android
```

### Debug-сборка

```sh
./gradlew :app:assembleDebug
```

APK появится здесь:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release-сборка

```sh
./gradlew :app:assembleRelease
```

APK появится здесь:

```text
app/build/outputs/apk/release/app-release.apk
```

Для release-сборки нужны параметры подписи. Их можно передать через `local.properties`, Gradle properties или переменные окружения:

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<пароль от хранилища>
RELEASE_KEY_ALIAS=<алиас ключа>
RELEASE_KEY_PASSWORD=<пароль от ключа>
```

### Установка на подключенный телефон

```sh
./gradlew :app:installDebug
```

Для установки release-варианта:

```sh
./gradlew :app:installRelease
```

### Тесты

```sh
./gradlew test
```

Запускает unit-тесты всех модулей (включая `core:network` и `core:data`).

---

## CI/CD и релизы

Сборка автоматизирована через GitHub Actions (`.github/workflows/`):

- **`ci.yml`** — на каждый пуш в `main` и на каждый Pull Request: собирает debug-APK и гоняет unit-тесты. APK доступен в артефактах запуска.
- **`release.yml`** — при пуше тега `v*` (например `v0.2.0`): проверяет SemVer-формат тега, запускает unit-тесты, собирает **подписанный** release-APK и публикует его в GitHub Releases с автогенерацией списка изменений.

### Авто-версия

`versionName` и `versionCode` вычисляются из git, вручную править `app/build.gradle.kts` перед релизом не нужно:

- `versionName` — последний релизный тег `v*` без ведущего `v` (`v0.2.0` → `0.2.0`). Пока релизных тегов нет — фолбэк `0.1.0`.
- `versionCode` — число коммитов (`git rev-list --count HEAD`), монотонно растёт. Фолбэк `1` вне git.

### Как выпустить релиз

1. Убедись, что секреты подписи добавлены в репозиторий (см. ниже) — это делается один раз.
2. На собранном и проверенном коммите создай тег и отправь его:

   ```sh
   git tag v0.2.0
   git push origin v0.2.0
   ```

3. GitHub Actions проверит тег, прогонит unit-тесты, соберёт подписанный APK и опубликует его на странице Releases. Имя файла: `eBookSender-v0.2.0.apk`.

Страница релиза будет создана с заголовком тега, например `v0.2.0`, автосгенерированным списком изменений GitHub и APK во вложениях. После публикации верхний бейдж **Версия** в README начнёт показывать этот последний релиз.

### Секреты подписи (один раз)

В **Settings → Secrets and variables → Actions** добавь четыре секрета:

| Секрет | Описание |
| --- | --- |
| `KEYSTORE_BASE64` | Релизный keystore, закодированный в base64 |
| `RELEASE_STORE_PASSWORD` | Пароль от keystore |
| `RELEASE_KEY_ALIAS` | Алиас ключа подписи |
| `RELEASE_KEY_PASSWORD` | Пароль от ключа |

Получить base64 от `release.keystore`:

```sh
base64 -w 0 release.keystore
```

> Keystore **не хранится** в репозитории (исключён в `.gitignore`) и пробрасывается в CI только через секреты.

---

## Архитектура проекта

Проект собран как модульное Android-приложение. В `settings.gradle.kts` подключены 14 модулей:

```text
app
core:model
core:common
core:domain
core:database
core:datastore
core:network
core:data
core:ui
feature:catalog
feature:manga
feature:opds
feature:settings
feature:transfer
```

Основные зоны ответственности:

| Модуль | Назначение |
| --- | --- |
| `app` | точка входа, Android manifest, DI, foreground services, обработка share-интентов |
| `core:model` | общие модели приложения |
| `core:common` | общие утилиты, кэш, форматирование, сетевые исключения |
| `core:domain` | классификация файлов, планирование путей, парсинг FTP URL и санитизация имен |
| `core:database` | Room-база, DAO и сущности |
| `core:datastore` | хранение настроек приложения |
| `core:network` | OPDS, Com-X, HTTP-клиенты и парсеры |
| `core:data` | репозитории, FTP-шлюз, каталог устройства, загрузка OPDS и манги |
| `core:ui` | тема, общие UI-компоненты, локализация, жесты, haptics |
| `feature:transfer` | экран отправки и управление очередью |
| `feature:catalog` | каталог подключенного устройства |
| `feature:opds` | OPDS-браузер |
| `feature:manga` | поиск, загрузка, избранное и подписки манги |
| `feature:settings` | настройки хранения, интерфейса, именования и обслуживания |

Дополнительная техническая карта проекта находится в [CODEX_PROJECT_MAP.md](CODEX_PROJECT_MAP.md).

---

## Технологии

| Технология | Для чего используется |
| --- | --- |
| Kotlin 2.0.21 | основной язык разработки |
| Jetpack Compose | декларативный UI |
| Material 3 | дизайн-система приложения |
| Hilt | внедрение зависимостей |
| Room 2.6.1 | локальная база данных |
| DataStore | настройки приложения |
| Apache Commons Net 3.13.0 | FTP-клиент |
| Android Keystore | защищенное хранение OPDS-учетных данных |
| kotlinx.serialization | JSON-сериализация |
| JSoup 1.18.3 | HTML/XML-парсинг для сетевых источников |
| Play Services Code Scanner | сканирование QR-кодов |
| Junrar 7.6.0 | чтение CBR/RAR-архивов |

---

## Участие в проекте

Сообщения о багах, идеи и pull request'ы приветствуются.

- Если нашли ошибку, создайте [Issue](../../issues) и опишите шаги воспроизведения.
- Если предлагаете изменение поведения, опишите сценарий пользователя и ожидаемый результат.
- Для изменений в коде создайте fork, отдельную ветку и pull request.
- Для переводов можно обновить JSON-файлы локализации или приложить новый перевод.

---

## Лицензия

Проект распространяется под лицензией **GNU General Public License v2.0**.

```text
eBookSender — FTP-клиент для отправки книг и манги на электронные читалки.
Copyright (C) 2026 CyberCat

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

Полный текст лицензии находится в [LICENSE](LICENSE).
