package com.cybercat.ebooksender.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.AppTheme
import com.cybercat.ebooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.ebooksender.model.MangaLoginMode
import com.cybercat.ebooksender.model.normalizeFtpRelativeRootPath
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            rootPath = normalizeFtpRelativeRootPath(
                preferences[ROOT_PATH] ?: DEFAULT_FTP_RELATIVE_ROOT_PATH
            ),
            booksFolderName = preferences[BOOKS_FOLDER_NAME] ?: "Books",
            documentsFolderName = preferences[DOCUMENTS_FOLDER_NAME] ?: "Documents",
            mangaFolderName = preferences[MANGA_FOLDER_NAME] ?: "Manga",
            defaultDocumentsTag = preferences[DEFAULT_DOCUMENTS_TAG] ?: "Untagged",
            defaultMangaSeries = preferences[DEFAULT_MANGA_SERIES] ?: "Unknown_Series",
            bookFileNameTemplate = preferences[BOOK_FILE_NAME_TEMPLATE] ?: "{title}",
            documentsFileNameTemplate = preferences[DOCUMENTS_FILE_NAME_TEMPLATE] ?: "{title}",
            mangaFileNameTemplate = preferences[MANGA_FILE_NAME_TEMPLATE] ?: "{series}_{volume}",
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
            enableHaptics = preferences[ENABLE_HAPTICS] ?: true,
            bypassVpnForLocalConnections = preferences[BYPASS_VPN_FOR_LOCAL_CONNECTIONS] ?: false,
            mangaLoginMode =
                preferences[MANGA_LOGIN_MODE]
                    ?.let { runCatching { MangaLoginMode.valueOf(it) }.getOrNull() }
                    ?: MangaLoginMode.Ask,
            theme =
                preferences[THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                    ?: AppTheme.System,
            warnOnDisconnectedRename = preferences[WARN_ON_DISCONNECTED_RENAME] ?: true,
            languageCode = preferences[LANGUAGE_CODE] ?: "system"
        )
    }

    suspend fun setRootPath(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ROOT_PATH] = normalizeFtpRelativeRootPath(value)
        }
    }

    suspend fun setBooksFolderName(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[BOOKS_FOLDER_NAME] = value.ifBlank { "Books" }
        }
    }

    suspend fun setDocumentsFolderName(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DOCUMENTS_FOLDER_NAME] = value.ifBlank { "Documents" }
        }
    }

    suspend fun setMangaFolderName(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MANGA_FOLDER_NAME] = value.ifBlank { "Manga" }
        }
    }

    suspend fun setUseDynamicColor(value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLOR] = value
        }
    }

    suspend fun setEnableHaptics(value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ENABLE_HAPTICS] = value
        }
    }

    suspend fun setBypassVpnForLocalConnections(value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[BYPASS_VPN_FOR_LOCAL_CONNECTIONS] = value
        }
    }

    suspend fun setMangaLoginMode(value: MangaLoginMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[MANGA_LOGIN_MODE] = value.name
        }
    }

    suspend fun setTheme(value: AppTheme) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME] = value.name
        }
    }

    suspend fun setWarnOnDisconnectedRename(value: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WARN_ON_DISCONNECTED_RENAME] = value
        }
    }

    suspend fun setLanguageCode(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LANGUAGE_CODE] = value
        }
    }

    suspend fun setDefaultDocumentsTag(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_DOCUMENTS_TAG] = value.ifBlank { "Untagged" }
        }
    }

    suspend fun setDefaultMangaSeries(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_MANGA_SERIES] = value.ifBlank { "Unknown_Series" }
        }
    }

    suspend fun setBookFileNameTemplate(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[BOOK_FILE_NAME_TEMPLATE] = value.ifBlank { "{title}" }
        }
    }

    suspend fun setDocumentsFileNameTemplate(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DOCUMENTS_FILE_NAME_TEMPLATE] = value.ifBlank { "{title}" }
        }
    }

    suspend fun setMangaFileNameTemplate(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MANGA_FILE_NAME_TEMPLATE] = value.ifBlank { "{series}_{volume}" }
        }
    }

    private companion object {
        val ROOT_PATH = stringPreferencesKey("root_path")
        val BOOKS_FOLDER_NAME = stringPreferencesKey("books_folder_name")
        val DOCUMENTS_FOLDER_NAME = stringPreferencesKey("documents_folder_name")
        val MANGA_FOLDER_NAME = stringPreferencesKey("manga_folder_name")
        val DEFAULT_DOCUMENTS_TAG = stringPreferencesKey("default_documents_tag")
        val DEFAULT_MANGA_SERIES = stringPreferencesKey("default_manga_series")
        val BOOK_FILE_NAME_TEMPLATE = stringPreferencesKey("book_file_name_template")
        val DOCUMENTS_FILE_NAME_TEMPLATE = stringPreferencesKey("documents_file_name_template")
        val MANGA_FILE_NAME_TEMPLATE = stringPreferencesKey("manga_file_name_template")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val ENABLE_HAPTICS = booleanPreferencesKey("enable_haptics")
        val BYPASS_VPN_FOR_LOCAL_CONNECTIONS =
            booleanPreferencesKey("bypass_vpn_for_local_connections")
        val MANGA_LOGIN_MODE = stringPreferencesKey("manga_login_mode")
        val THEME = stringPreferencesKey("theme")
        val WARN_ON_DISCONNECTED_RENAME = booleanPreferencesKey("warn_on_disconnected_rename")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }
}
