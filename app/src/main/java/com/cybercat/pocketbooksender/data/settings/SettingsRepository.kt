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
            useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
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

    private companion object {
        val ROOT_PATH = stringPreferencesKey("root_path")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
}
