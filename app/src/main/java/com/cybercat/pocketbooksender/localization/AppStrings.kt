package com.cybercat.pocketbooksender.localization

class AppStrings(
    val languageCode: String,
    val languageName: String,
    private val translationMap: Map<String, String>,
    private val fallbackMap: Map<String, String>
) {
    fun get(key: String): String {
        return translationMap[key] ?: fallbackMap[key] ?: key
    }

    fun get(key: String, vararg args: Any): String {
        val pattern = get(key)
        return try {
            String.format(pattern, *args)
        } catch (e: Exception) {
            pattern
        }
    }

    // App name & Navigation
    val appName: String get() = get("app_name")
    val navSend: String get() = get("nav_send")
    val navCatalog: String get() = get("nav_catalog")
    val navWeb: String get() = get("nav_web")
    val navSettings: String get() = get("nav_settings")

    // Settings Screen
    val settingsTitle: String get() = get("settings_title")
    val settingsStorageSection: String get() = get("settings_storage_section")
    val settingsRootPath: String get() = get("settings_root_path")
    val settingsBooksFolder: String get() = get("settings_books_folder")
    val settingsDocsFolder: String get() = get("settings_docs_folder")
    val settingsMangaFolder: String get() = get("settings_manga_folder")
    
    val settingsNamingSection: String get() = get("settings_naming_section")
    val settingsNamingTokens: String get() = get("settings_naming_tokens")
    val settingsNamingBooksTemplate: String get() = get("settings_naming_books_template")
    val settingsNamingDocsTemplate: String get() = get("settings_naming_docs_template")
    val settingsNamingMangaTemplate: String get() = get("settings_naming_manga_template")
    val settingsNamingDocsTag: String get() = get("settings_naming_docs_tag")
    val settingsNamingMangaSeries: String get() = get("settings_naming_manga_series")
    val settingsNamingPreview: String get() = get("settings_naming_preview")

    val settingsInterfaceSection: String get() = get("settings_interface_section")
    val settingsDynamicColor: String get() = get("settings_dynamic_color")
    val settingsDynamicColorDesc: String get() = get("settings_dynamic_color_desc")
    val settingsHaptic: String get() = get("settings_haptic")
    val settingsHapticDesc: String get() = get("settings_haptic_desc")
    val settingsWarnDisconnected: String get() = get("settings_warn_disconnected")
    val settingsWarnDisconnectedDesc: String get() = get("settings_warn_disconnected_desc")
    
    val settingsTheme: String get() = get("settings_theme")
    val settingsThemeLight: String get() = get("settings_theme_light")
    val settingsThemeDark: String get() = get("settings_theme_dark")
    val settingsThemeSystem: String get() = get("settings_theme_system")
    
    val settingsMaintenanceSection: String get() = get("settings_maintenance_section")
    val settingsClearCache: String get() = get("settings_clear_cache")
    
    val settingsDialogTitle: String get() = get("settings_dialog_title")
    val settingsDialogBody: String get() = get("settings_dialog_body")
    val settingsDialogConfirm: String get() = get("settings_dialog_confirm")
    val settingsDialogCancel: String get() = get("settings_dialog_cancel")
    
    val settingsNothingToClear: String get() = get("settings_nothing_to_clear")
    val settingsClearedCache: String get() = get("settings_cleared_cache")
    val settingsRenamedOnDevice: String get() = get("settings_renamed_on_device")
    val settingsRenameFailedExists: String get() = get("settings_rename_failed_exists")
    val settingsRenameFailedError: String get() = get("settings_rename_failed_error")
    val settingsLanguageOption: String get() = get("settings_language_option")
    val settingsLanguageDialogTitle: String get() = get("settings_language_dialog_title")
    val settingsLanguageSystem: String get() = get("settings_language_system")

    // Send Screen
    val sendTitle: String get() = get("send_title")
    val sendLabelFtp: String get() = get("send_label_ftp")
    val sendPlaceholderFtp: String get() = get("send_placeholder_ftp")
    val sendBtnScanQr: String get() = get("send_btn_scan_qr")
    val sendBtnConnecting: String get() = get("send_btn_connecting")
    val sendBtnConnect: String get() = get("send_btn_connect")
    val sendBtnDisconnect: String get() = get("send_btn_disconnect")
    val sendBtnAddFiles: String get() = get("send_btn_add_files")
    val sendBtnUpload: String get() = get("send_btn_upload")
    val sendUploadedHeader: String get() = get("send_uploaded_header")
    val sendUploadedBooksCount: String get() = get("send_uploaded_books_count")
    val sendUploadComplete: String get() = get("send_upload_complete")
    val sendSendingStatus: String get() = get("send_sending_status")
    val sendProgressDetail: String get() = get("send_progress_detail")
    val sendProgressDetailFailed: String get() = get("send_progress_detail_failed")
    val sendRenameMangaTitle: String get() = get("send_rename_manga_title")
    val sendRenameMangaSeries: String get() = get("send_rename_manga_series")
    val sendRenameMangaApply: String get() = get("send_rename_manga_apply")
    val sendRenameMangaCancel: String get() = get("send_rename_manga_cancel")
    val sendStatusUploading: String get() = get("send_status_uploading")
    
    // Catalog Screen
    val catalogDeleteTitle: String get() = get("catalog_delete_title")
    val catalogDeleteBody: String get() = get("catalog_delete_body")
    val catalogDeleteBtn: String get() = get("catalog_delete_btn")
    val catalogDeleteCancel: String get() = get("catalog_delete_cancel")
    val catalogDeleteErrorTitle: String get() = get("catalog_delete_error_title")
    val catalogDeleteErrorBtn: String get() = get("catalog_delete_error_btn")
    val catalogDeletingState: String get() = get("catalog_deleting_state")
    val catalogSelectedCount: String get() = get("catalog_selected_count")
    val catalogTitle: String get() = get("catalog_title")

    // OPDS Screen
    val opdsAddTitle: String get() = get("opds_add_title")
    val opdsUrlField: String get() = get("opds_url_field")
    val opdsUrlPlaceholder: String get() = get("opds_url_placeholder")
    val opdsTitleField: String get() = get("opds_title_field")
    val opdsBtnSave: String get() = get("opds_btn_save")
    val opdsBtnCancel: String get() = get("opds_btn_cancel")
    val opdsBtnDownload: String get() = get("opds_btn_download")
    val opdsSearchPlaceholder: String get() = get("opds_search_placeholder")
    val opdsSearchCatalog: String get() = get("opds_search_catalog")
    val opdsNoSearchSupport: String get() = get("opds_no_search_support")
    
    // Manga Pane
    val mangaSearchManga: String get() = get("manga_search_manga")
    val mangaSearchMangaPlaceholder: String get() = get("manga_search_manga_placeholder")
    val mangaDoNotRemember: String get() = get("manga_do_not_remember")
    val mangaUsername: String get() = get("manga_username")
    val mangaPassword: String get() = get("manga_password")
    val mangaBtnLogin: String get() = get("manga_btn_login")
    val mangaBtnOpen: String get() = get("manga_btn_open")
    val mangaStatusDone: String get() = get("manga_status_done")
}

val LocalStrings = androidx.compose.runtime.staticCompositionLocalOf<AppStrings> {
    error("No AppStrings provided")
}

