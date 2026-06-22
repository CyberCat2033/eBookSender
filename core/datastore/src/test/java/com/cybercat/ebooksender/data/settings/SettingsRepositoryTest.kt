package com.cybercat.ebooksender.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cybercat.ebooksender.model.AppTheme
import com.cybercat.ebooksender.model.MangaLoginMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "test_settings.preferences_pb") }
        )
    }

    private val repository by lazy {
        SettingsRepository(dataStore)
    }

    @Test
    fun testDefaultSettings() = runBlocking {
        val settings = repository.settings.first()
        assertEquals("", settings.rootPath)
        assertEquals("Books", settings.booksFolderName)
        assertEquals("Documents", settings.documentsFolderName)
        assertEquals("Manga", settings.mangaFolderName)
        assertTrue(settings.useDynamicColor)
        assertTrue(settings.enableHaptics)
        assertFalse(settings.bypassVpnForLocalConnections)
        assertEquals(MangaLoginMode.Ask, settings.mangaLoginMode)
        assertEquals(AppTheme.System, settings.theme)
        assertTrue(settings.warnOnDisconnectedRename)
        assertEquals("system", settings.languageCode)
    }

    @Test
    fun testSetRootPathNormalizes() = runBlocking {
        repository.setRootPath("some/path")
        assertEquals("some/path", repository.settings.first().rootPath)

        repository.setRootPath("")
        assertEquals("", repository.settings.first().rootPath)

        repository.setRootPath("/leading/slash/")
        assertEquals("leading/slash", repository.settings.first().rootPath)
    }

    @Test
    fun testSetFolderNamesWithFallbacks() = runBlocking {
        repository.setBooksFolderName("MyBooks")
        assertEquals("MyBooks", repository.settings.first().booksFolderName)

        repository.setBooksFolderName(" ")
        assertEquals("Books", repository.settings.first().booksFolderName)

        repository.setDocumentsFolderName("")
        assertEquals("Documents", repository.settings.first().documentsFolderName)

        repository.setMangaFolderName("")
        assertEquals("Manga", repository.settings.first().mangaFolderName)
    }

    @Test
    fun testResetToDefaults() = runBlocking {
        repository.setBooksFolderName("CustomBooks")
        repository.setEnableHaptics(false)
        repository.setTheme(AppTheme.Dark)

        assertEquals("CustomBooks", repository.settings.first().booksFolderName)
        assertFalse(repository.settings.first().enableHaptics)
        assertEquals(AppTheme.Dark, repository.settings.first().theme)

        repository.resetToDefaults()

        val settings = repository.settings.first()
        assertEquals("Books", settings.booksFolderName)
        assertTrue(settings.enableHaptics)
        assertEquals(AppTheme.System, settings.theme)
    }

    @Test
    fun testAllSettingsSetters() = runBlocking {
        repository.setUseDynamicColor(false)
        repository.setEnableHaptics(false)
        repository.setBypassVpnForLocalConnections(true)
        repository.setMangaLoginMode(MangaLoginMode.WebView)
        repository.setTheme(AppTheme.Light)
        repository.setWarnOnDisconnectedRename(false)
        repository.setLanguageCode("ru")
        repository.setDefaultDocumentsTag("Tagged")
        repository.setDefaultMangaSeries("Subscribed")
        repository.setBookFileNameTemplate("{author} - {title}")
        repository.setDocumentsFileNameTemplate("Doc {title}")
        repository.setMangaFileNameTemplate("{series} {volume}")

        val settings = repository.settings.first()
        assertFalse(settings.useDynamicColor)
        assertFalse(settings.enableHaptics)
        assertTrue(settings.bypassVpnForLocalConnections)
        assertEquals(MangaLoginMode.WebView, settings.mangaLoginMode)
        assertEquals(AppTheme.Light, settings.theme)
        assertFalse(settings.warnOnDisconnectedRename)
        assertEquals("ru", settings.languageCode)
        assertEquals("Tagged", settings.defaultDocumentsTag)
        assertEquals("Subscribed", settings.defaultMangaSeries)
        assertEquals("{author} - {title}", settings.bookFileNameTemplate)
        assertEquals("Doc {title}", settings.documentsFileNameTemplate)
        assertEquals("{series} {volume}", settings.mangaFileNameTemplate)
    }
}
