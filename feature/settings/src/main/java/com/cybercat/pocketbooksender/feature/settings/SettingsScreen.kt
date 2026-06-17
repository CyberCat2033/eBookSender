package com.cybercat.pocketbooksender.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.ui.LocalAdaptiveLayoutInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    scrollState: ScrollState,
    onRootPathChanged: (String) -> Unit,
    onBooksFolderNameChanged: (String) -> Unit,
    onDocumentsFolderNameChanged: (String) -> Unit,
    onMangaFolderNameChanged: (String) -> Unit,
    onDefaultDocumentsTagChanged: (String) -> Unit,
    onDefaultMangaSeriesChanged: (String) -> Unit,
    onBookFileNameTemplateChanged: (String) -> Unit,
    onDocumentsFileNameTemplateChanged: (String) -> Unit,
    onMangaFileNameTemplateChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHapticFeedbackEnabledChanged: (Boolean) -> Unit,
    onBypassVpnForLocalConnectionsChanged: (Boolean) -> Unit,
    onClearDownloadCache: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onWarnOnDisconnectedRenameChanged: (Boolean) -> Unit,
    onConfirmPendingRename: () -> Unit,
    onCancelPendingRename: () -> Unit,
    onLogoutAll: () -> Unit,
    onConfirmLogoutAll: () -> Unit,
    onDismissLogoutWarning: () -> Unit
) {
    val strings = LocalStrings.current
    val adaptiveLayout = LocalAdaptiveLayoutInfo.current

    var focusedNamingTemplateSlot by remember { mutableStateOf<NamingTemplateSlot?>(null) }
    var showRenameWarning by remember { mutableStateOf(false) }
    var pendingRenameSnapshot by remember { mutableStateOf<PendingRename?>(null) }
    var folderFieldResetKey by remember { mutableStateOf(0) }
    val hadPendingRename = remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.pendingRename) {
        val hasPending = state.pendingRename != null
        if (hasPending) {
            pendingRenameSnapshot = state.pendingRename
            showRenameWarning = true
        }
        if (hadPendingRename.value && !hasPending) folderFieldResetKey++
        hadPendingRename.value = hasPending
    }

    fun updateFocusedNamingTemplateSlot(slot: NamingTemplateSlot, isFocused: Boolean) {
        focusedNamingTemplateSlot = when {
            isFocused -> slot
            focusedNamingTemplateSlot == slot -> null
            else -> focusedNamingTemplateSlot
        }
    }

    if (showRenameWarning) {
        pendingRenameSnapshot?.let { pending ->
            SettingsRenameWarningDialog(
                pending = pending,
                enableHaptics = state.settings.enableHaptics,
                onConfirm = {
                    showRenameWarning = false
                    onConfirmPendingRename()
                },
                onCancel = {
                    showRenameWarning = false
                    onCancelPendingRename()
                }
            )
        }
    }

    if (state.showLogoutWarning) {
        SettingsLogoutWarningDialog(
            enableHaptics = state.settings.enableHaptics,
            onConfirm = onConfirmLogoutAll,
            onDismiss = onDismissLogoutWarning
        )
    }

    if (showLanguageDialog) {
        SettingsLanguageDialog(
            state = state,
            enableHaptics = state.settings.enableHaptics,
            onLanguageChanged = onLanguageChanged,
            onDismiss = { showLanguageDialog = false }
        )
    }

    Scaffold(
        topBar = {
            val containerColor = MaterialTheme.colorScheme.background
            val contentColor = MaterialTheme.colorScheme.onBackground
            key(containerColor, contentColor) {
                TopAppBar(
                    title = { Text(strings.settingsTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = containerColor,
                        scrolledContainerColor = containerColor,
                        navigationIconContentColor = contentColor,
                        titleContentColor = contentColor,
                        actionIconContentColor = contentColor
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = adaptiveLayout.screenHorizontalPadding,
                        vertical = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StorageSettingsSection(
                    state = state,
                    folderFieldResetKey = folderFieldResetKey,
                    onRootPathChanged = onRootPathChanged,
                    onBooksFolderNameChanged = onBooksFolderNameChanged,
                    onDocumentsFolderNameChanged = onDocumentsFolderNameChanged,
                    onMangaFolderNameChanged = onMangaFolderNameChanged
                )

                NamingSettingsSection(
                    state = state,
                    focusedNamingTemplateSlot = focusedNamingTemplateSlot,
                    onFocusChanged = ::updateFocusedNamingTemplateSlot,
                    onDefaultDocumentsTagChanged = onDefaultDocumentsTagChanged,
                    onDefaultMangaSeriesChanged = onDefaultMangaSeriesChanged,
                    onBookFileNameTemplateChanged = onBookFileNameTemplateChanged,
                    onDocumentsFileNameTemplateChanged = onDocumentsFileNameTemplateChanged,
                    onMangaFileNameTemplateChanged = onMangaFileNameTemplateChanged
                )

                InterfaceSettingsSection(
                    state = state,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onBypassVpnForLocalConnectionsChanged = onBypassVpnForLocalConnectionsChanged,
                    onHapticFeedbackEnabledChanged = onHapticFeedbackEnabledChanged,
                    onWarnOnDisconnectedRenameChanged = onWarnOnDisconnectedRenameChanged,
                    onThemeChanged = onThemeChanged,
                    onLanguageClick = { showLanguageDialog = true }
                )

                MaintenanceSettingsSection(
                    state = state,
                    onClearDownloadCache = onClearDownloadCache,
                    onLogoutAll = onLogoutAll,
                    onClearStatusMessage = onClearStatusMessage
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
