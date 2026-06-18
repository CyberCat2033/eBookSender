package com.cybercat.ebooksender.localization

import android.content.Context
import android.util.Log
import com.cybercat.ebooksender.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalizationManager"

data class LocaleInfo(
    val code: String,
    val name: String,
    val isExternal: Boolean,
    val filePath: String,
    val lastModified: Long
)

@Singleton
class LocalizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _availableLocales = MutableStateFlow<List<LocaleInfo>>(emptyList())
    val availableLocales: StateFlow<List<LocaleInfo>> = _availableLocales

    private val _currentStrings = MutableStateFlow<AppStrings>(createFallbackStrings())
    val currentStrings: StateFlow<AppStrings> = _currentStrings

    private val scope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    init {
        scope.launch {
            // Listen to settings changes (languageCode & rootPath) and reload translations
            combine(
                settingsRepository.settings,
                _availableLocales
            ) { settings, locales ->
                Pair(settings, locales)
            }.collect { (settings, locales) ->
                loadStrings(settings.rootPath, settings.languageCode, locales)
            }
        }

        // Perform initial scan of locales
        scope.launch {
            scanLocales()
        }
    }

    suspend fun scanLocales() = withContext(Dispatchers.IO) {
        val locales = mutableListOf<LocaleInfo>()

        // 1. Scan assets
        try {
            val assetFiles = context.assets.list("locales") ?: emptyArray()
            for (filename in assetFiles) {
                if (filename.endsWith(".json")) {
                    try {
                        val content = context.assets.open("locales/$filename").use { it.reader().readText() }
                        val metadata = parseMetadata(content)
                        if (metadata != null) {
                            locales.add(
                                LocaleInfo(
                                    code = metadata.first,
                                    name = metadata.second,
                                    isExternal = false,
                                    filePath = "assets/locales/$filename",
                                    lastModified = 0L
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse asset locale: $filename", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list asset locales", e)
        }

        // 2. Scan external storage locales
        try {
            val settings = settingsRepository.settings.first()
            val extLocalesDir = File(settings.rootPath, "eBookSender/locales")
            if (extLocalesDir.exists() && extLocalesDir.isDirectory) {
                val files = extLocalesDir.listFiles { _, name -> name.endsWith(".json") }
                if (files != null) {
                    for (file in files) {
                        try {
                            val content = file.readText()
                            val metadata = parseMetadata(content)
                            if (metadata != null) {
                                locales.add(
                                    LocaleInfo(
                                        code = metadata.first,
                                        name = metadata.second,
                                        isExternal = true,
                                        filePath = file.absolutePath,
                                        lastModified = file.lastModified()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse external locale: ${file.name}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan external locales", e)
        }

        // 3. Resolve Collisions:
        // Group by language code, and for each group, pick the best one:
        // Prioritize: external > internal, then newest lastModified > oldest.
        val filteredLocales = locales.groupBy { it.code }
            .map { (_, list) ->
                list.sortedWith(
                    compareByDescending<LocaleInfo> { it.isExternal }
                        .thenByDescending { it.lastModified }
                ).first()
            }
            .sortedBy { it.name }

        _availableLocales.value = filteredLocales
    }

    private fun parseMetadata(content: String): Pair<String, String>? {
        return try {
            val map = json.decodeFromString<Map<String, String>>(content)
            val code = map["meta_language_code"]
            val name = map["meta_language_name"]
            if (code != null && name != null) Pair(code, name) else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun loadStrings(
        rootPath: String,
        targetCode: String,
        locales: List<LocaleInfo>
    ) = withContext(Dispatchers.IO) {
        val englishFallbackMap = loadAssetMap("en.json") ?: emptyMap()
        
        // Scan external path again just in case the folder was newly created or updated
        // but scanLocales hasn't finished yet
        var actualTargetCode = targetCode
        if (actualTargetCode == "system") {
            val systemLanguage = java.util.Locale.getDefault().language
            actualTargetCode = if (locales.any { it.code == systemLanguage }) systemLanguage else "en"
        }

        val chosenLocale = locales.find { it.code == actualTargetCode }
        val translationMap = if (chosenLocale != null) {
            if (chosenLocale.isExternal) {
                loadExternalMap(chosenLocale.filePath)
            } else {
                val assetFilename = chosenLocale.filePath.substringAfterLast("/")
                loadAssetMap(assetFilename)
            }
        } else {
            null
        } ?: englishFallbackMap

        val languageName = chosenLocale?.name ?: "English"
        val languageCode = chosenLocale?.code ?: "en"

        _currentStrings.value = AppStrings(
            languageCode = languageCode,
            languageName = languageName,
            translationMap = translationMap,
            fallbackMap = englishFallbackMap
        )
    }

    private fun loadAssetMap(filename: String): Map<String, String>? {
        return try {
            val content = context.assets.open("locales/$filename").use { it.reader().readText() }
            json.decodeFromString<Map<String, String>>(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset map: $filename", e)
            null
        }
    }

    private fun loadExternalMap(filePath: String): Map<String, String>? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val content = file.readText()
                json.decodeFromString<Map<String, String>>(content)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load external map: $filePath", e)
            null
        }
    }

    private fun createFallbackStrings(): AppStrings {
        return AppStrings("en", "English", emptyMap(), emptyMap())
    }
}
