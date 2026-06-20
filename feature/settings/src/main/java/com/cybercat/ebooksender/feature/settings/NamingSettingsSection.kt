package com.cybercat.ebooksender.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.CatalogFallbackNames

@Composable
internal fun NamingSettingsSection(
    state: SettingsUiState,
    focusedNamingTemplateSlot: NamingTemplateSlot?,
    onFocusChanged: (NamingTemplateSlot, Boolean) -> Unit,
    onDefaultDocumentsTagChanged: (String) -> Unit,
    onDefaultMangaSeriesChanged: (String) -> Unit,
    onBookFileNameTemplateChanged: (String) -> Unit,
    onDocumentsFileNameTemplateChanged: (String) -> Unit,
    onMangaFileNameTemplateChanged: (String) -> Unit
) {
    val strings = LocalStrings.current
    SettingsSection(title = strings.settingsNamingSection) {
        var namingTokensContainerCoordinates by remember {
            mutableStateOf<LayoutCoordinates?>(null)
        }
        var namingTokensTargetY by remember { mutableStateOf(0) }
        var namingTokensHeightPx by remember { mutableStateOf(0) }
        val commonTokens = mapOf(
            "title" to strings.settingsNamingExampleTitle,
            "author" to strings.settingsNamingExampleAuthor,
            "year" to strings.settingsNamingExampleYear,
            "ext" to "epub",
            "original" to strings.settingsNamingExampleOriginal,
            "tag" to strings.settingsNamingExampleTag,
            "series" to strings.settingsNamingExampleSeries,
            "index" to strings.settingsNamingExampleIndex,
            "volume" to strings.settingsNamingExampleVolume,
            "publisher" to strings.settingsNamingExamplePublisher
        )
        val bookTokens = commonTokens + mapOf(
            "tag" to "",
            "volume" to ""
        )
        val documentTokens = commonTokens + mapOf(
            "author" to "",
            "series" to "",
            "index" to "",
            "volume" to "",
            "publisher" to ""
        )
        val mangaTokens = commonTokens + mapOf(
            "author" to "",
            "tag" to "",
            "year" to "",
            "publisher" to ""
        )

        var bookTemplatePreview by remember(state.settings.bookFileNameTemplate) {
            mutableStateOf(state.settings.bookFileNameTemplate)
        }
        var docsTemplatePreview by remember(state.settings.documentsFileNameTemplate) {
            mutableStateOf(state.settings.documentsFileNameTemplate)
        }
        var mangaTemplatePreview by remember(state.settings.mangaFileNameTemplate) {
            mutableStateOf(state.settings.mangaFileNameTemplate)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    namingTokensContainerCoordinates = coordinates
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NamingTokensAnchor(
                    slot = NamingTemplateSlot.Books,
                    activeSlot = focusedNamingTemplateSlot,
                    tokenHeightPx = namingTokensHeightPx,
                    containerCoordinates = namingTokensContainerCoordinates,
                    onPositioned = { namingTokensTargetY = it }
                )

                NamingTemplateBlock(
                    value = state.settings.bookFileNameTemplate,
                    onValueChange = onBookFileNameTemplateChanged,
                    label = strings.settingsNamingBooksTemplate,
                    imeAction = ImeAction.Next,
                    onPreviewChange = { bookTemplatePreview = it },
                    previewLabel = strings.get("settings_naming_preview", strings.categoryBooks),
                    previewTemplate = bookTemplatePreview,
                    exampleTokens = bookTokens,
                    folderName = state.settings.booksFolderName,
                    groupFolder = strings.settingsNamingExampleAuthor,
                    onFocusChanged = { isFocused ->
                        onFocusChanged(NamingTemplateSlot.Books, isFocused)
                    },
                    validation = { it.trim().ifBlank { "{title}" } }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NamingTokensAnchor(
                        slot = NamingTemplateSlot.Documents,
                        activeSlot = focusedNamingTemplateSlot,
                        tokenHeightPx = namingTokensHeightPx,
                        containerCoordinates = namingTokensContainerCoordinates,
                        onPositioned = { namingTokensTargetY = it }
                    )

                    ValidatedSettingsField(
                        value = state.settings.defaultDocumentsTag,
                        onValueChange = onDefaultDocumentsTagChanged,
                        label = strings.settingsNamingDocsTag,
                        imeAction = ImeAction.Next,
                        validation = {
                            it.trim().ifBlank { CatalogFallbackNames.UNTAGGED_DOCUMENTS }
                        }
                    )
                    NamingTemplateBlock(
                        value = state.settings.documentsFileNameTemplate,
                        onValueChange = onDocumentsFileNameTemplateChanged,
                        label = strings.settingsNamingDocsTemplate,
                        imeAction = ImeAction.Next,
                        onPreviewChange = { docsTemplatePreview = it },
                        previewLabel = strings.get(
                            "settings_naming_preview",
                            strings.categoryDocuments
                        ),
                        previewTemplate = docsTemplatePreview,
                        exampleTokens = documentTokens,
                        folderName = state.settings.documentsFolderName,
                        groupFolder = strings.settingsNamingExampleTag,
                        onFocusChanged = { isFocused ->
                            onFocusChanged(NamingTemplateSlot.Documents, isFocused)
                        },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NamingTokensAnchor(
                        slot = NamingTemplateSlot.Manga,
                        activeSlot = focusedNamingTemplateSlot,
                        tokenHeightPx = namingTokensHeightPx,
                        containerCoordinates = namingTokensContainerCoordinates,
                        onPositioned = { namingTokensTargetY = it }
                    )

                    ValidatedSettingsField(
                        value = state.settings.defaultMangaSeries,
                        onValueChange = onDefaultMangaSeriesChanged,
                        label = strings.settingsNamingMangaSeries,
                        imeAction = ImeAction.Next,
                        validation = {
                            it.trim().ifBlank { CatalogFallbackNames.UNKNOWN_MANGA_SERIES }
                        }
                    )
                    NamingTemplateBlock(
                        value = state.settings.mangaFileNameTemplate,
                        onValueChange = onMangaFileNameTemplateChanged,
                        label = strings.settingsNamingMangaTemplate,
                        imeAction = ImeAction.Done,
                        onPreviewChange = { mangaTemplatePreview = it },
                        previewLabel = strings.get(
                            "settings_naming_preview",
                            strings.categoryManga
                        ),
                        previewTemplate = mangaTemplatePreview,
                        exampleTokens = mangaTokens,
                        folderName = state.settings.mangaFolderName,
                        groupFolder = strings.settingsNamingExampleSeries,
                        extension = "cbz",
                        onFocusChanged = { isFocused ->
                            onFocusChanged(NamingTemplateSlot.Manga, isFocused)
                        },
                        validation = { it.trim().ifBlank { "{series}_{volume}" } }
                    )
                }
            }

            if (focusedNamingTemplateSlot != null) {
                MovingNamingTokensHint(
                    text = focusedNamingTemplateSlot.tokensHint,
                    targetOffsetY = namingTokensTargetY,
                    onHeightChanged = { namingTokensHeightPx = it }
                )
            }
        }
    }
}

private val NamingTemplateSlot.tokensHint: String
    @Composable
    get() {
        val strings = LocalStrings.current
        return when (this) {
            NamingTemplateSlot.Books -> strings.settingsNamingBooksTokens
            NamingTemplateSlot.Documents -> strings.settingsNamingDocsTokens
            NamingTemplateSlot.Manga -> strings.settingsNamingMangaTokens
        }
    }
