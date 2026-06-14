package com.cybercat.pocketbooksender.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cybercat.pocketbooksender.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            rootPath = preferences[ROOT_PATH] ?: "/mnt/ext1",
            defaultProgrammingTag = preferences[DEFAULT_PROGRAMMING_TAG] ?: "Untagged",
            defaultMangaSeries = preferences[DEFAULT_MANGA_SERIES] ?: "Unknown_Series",
            bookFileNameTemplate = preferences[BOOK_FILE_NAME_TEMPLATE] ?: "{title}",
            programmingFileNameTemplate = preferences[PROGRAMMING_FILE_NAME_TEMPLATE] ?: "{title}",
            mangaFileNameTemplate = preferences[MANGA_FILE_NAME_TEMPLATE] ?: "{volume}",
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
            enableHaptics = preferences[ENABLE_HAPTICS] ?: true,
        )
    }

    suspend fun setRootPath(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ROOT_PATH] = value.ifBlank { "/mnt/ext1" }
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

    suspend fun setDefaultProgrammingTag(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_PROGRAMMING_TAG] = value.ifBlank { "Untagged" }
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

    suspend fun setProgrammingFileNameTemplate(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PROGRAMMING_FILE_NAME_TEMPLATE] = value.ifBlank { "{title}" }
        }
    }

    suspend fun setMangaFileNameTemplate(value: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MANGA_FILE_NAME_TEMPLATE] = value.ifBlank { "{volume}" }
        }
    }

    private companion object {
        val ROOT_PATH = stringPreferencesKey("root_path")
        val DEFAULT_PROGRAMMING_TAG = stringPreferencesKey("default_programming_tag")
        val DEFAULT_MANGA_SERIES = stringPreferencesKey("default_manga_series")
        val BOOK_FILE_NAME_TEMPLATE = stringPreferencesKey("book_file_name_template")
        val PROGRAMMING_FILE_NAME_TEMPLATE = stringPreferencesKey("programming_file_name_template")
        val MANGA_FILE_NAME_TEMPLATE = stringPreferencesKey("manga_file_name_template")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val ENABLE_HAPTICS = booleanPreferencesKey("enable_haptics")
    }
}
