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
    val categoryBooks: String get() = get("category_books")
    val categoryDocuments: String get() = get("category_documents")
    val categoryManga: String get() = get("category_manga")

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
    
    val sendLabelAddBooksDesc: String get() = get("send_label_add_books_desc")
    val sendBatchRenameDesc: String get() = get("send_batch_rename_desc")
    val sendStatusCheckingFtp: String get() = get("send_status_checking_ftp")
    val sendBtnClearQueue: String get() = get("send_btn_clear_queue")
    val sendBtnCollapseUploaded: String get() = get("send_btn_collapse_uploaded")
    val sendHeaderConnectPocketbook: String get() = get("send_header_connect_pocketbook")
    val sendActionEditDetails: String get() = get("send_action_edit_details")
    val sendActionHideDetails: String get() = get("send_action_hide_details")
    val sendMsgNoFiles: String get() = get("send_msg_no_files")
    val sendMsgConnected: String get() = get("send_msg_connected")
    val sendHeaderQueue: String get() = get("send_header_queue")
    val sendBtnRemove: String get() = get("send_btn_remove")
    val sendScanQrDesc: String get() = get("send_scan_qr_desc")
    val sendBtnShowUploaded: String get() = get("send_btn_show_uploaded")
    val sendStatusChecking: String get() = get("send_status_checking")

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

    val catalogStatusCannotRead: String get() = get("catalog_status_cannot_read")
    val catalogMsgEmpty: String get() = get("catalog_msg_empty")
    val catalogConnectPrompt: String get() = get("catalog_connect_prompt")
    val catalogActionDeleteSelected: String get() = get("catalog_action_delete_selected")
    val catalogActionEnterEdit: String get() = get("catalog_action_enter_edit")
    val catalogActionExitEdit: String get() = get("catalog_action_exit_edit")
    val catalogLabelLastRead: String get() = get("catalog_label_last_read")
    val catalogLabelLatest: String get() = get("catalog_label_latest")
    val catalogNoBooksFound: String get() = get("catalog_no_books_found")
    val catalogNoFiles: String get() = get("catalog_no_files")
    val catalogStatusNotStarted: String get() = get("catalog_status_not_started")
    val catalogPageCurrent: String get() = get("catalog_page_current")
    val catalogPageRatio: String get() = get("catalog_page_ratio")
    val catalogNotConnected: String get() = get("catalog_not_connected")
    val catalogReadPercentage: String get() = get("catalog_read_percentage")
    val catalogReadingTitle: String get() = get("catalog_reading_title")
    val catalogReadingDesc: String get() = get("catalog_reading_desc")
    val catalogLabelSeries: String get() = get("catalog_label_series")
    val catalogGroupFilesCount: String get() = get("catalog_group_files_count")
    val catalogGroupCompletedCount: String get() = get("catalog_group_completed_count")
    val catalogGroupProgressCount: String get() = get("catalog_group_progress_count")

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
    val opdsStatusOpening: String get() = get("opds_status_opening")
    val opdsRelStart: String get() = get("opds_rel_start")
    val opdsRelNext: String get() = get("opds_rel_next")
    val opdsRelPrevious: String get() = get("opds_rel_previous")
    val opdsRelUp: String get() = get("opds_rel_up")
    val opdsRelOpen: String get() = get("opds_rel_open")
    val opdsCatalogEmpty: String get() = get("opds_catalog_empty")
    val genericSelectedCount: String get() = get("generic_selected_count")
    
    // Manga Pane
    val mangaLoginTitle: String get() = get("manga_login_title")
    val mangaSearchManga: String get() = get("manga_search_manga")
    val mangaSearchMangaPlaceholder: String get() = get("manga_search_manga_placeholder")
    val mangaDoNotRemember: String get() = get("manga_do_not_remember")
    val mangaUsername: String get() = get("manga_username")
    val mangaPassword: String get() = get("manga_password")
    val mangaBtnLogin: String get() = get("manga_btn_login")
    val mangaBtnOpen: String get() = get("manga_btn_open")
    val mangaStatusDone: String get() = get("manga_status_done")
    val mangaHeaderSaved: String get() = get("manga_header_saved")
    val mangaBtnCheckNew: String get() = get("manga_btn_check_new")
    val mangaBtnChecking: String get() = get("manga_btn_checking")
    val mangaSearchResults: String get() = get("manga_search_results")
    val mangaDownloadPreparing: String get() = get("manga_download_preparing")
    val mangaDownloadPreparingChapters: String get() = get("manga_download_preparing_chapters")
    val mangaDownloadOneChapter: String get() = get("manga_download_one_chapter")
    val mangaDownloadChaptersCount: String get() = get("manga_download_chapters_count")
    val mangaSeriesChaptersSummary: String get() = get("manga_series_chapters_summary")
    val mangaLastDownloaded: String get() = get("manga_last_downloaded")
    val mangaLastRead: String get() = get("manga_last_read")
    val mangaBtnFavorite: String get() = get("manga_btn_favorite")
    val mangaBtnAddFavorite: String get() = get("manga_btn_add_favorite")
    val mangaBtnSubscribed: String get() = get("manga_btn_subscribed")
    val mangaBtnSubscribe: String get() = get("manga_btn_subscribe")
    val mangaChapterNumber: String get() = get("manga_chapter_number")
    val mangaLoadingSource: String get() = get("manga_loading_source")

    // Additional Send & Catalog keys
    val sendStatusPending: String get() = get("send_status_pending")
    val sendStatusPreparing: String get() = get("send_status_preparing")
    val sendStatusUploaded: String get() = get("send_status_uploaded")
    val sendStatusFailed: String get() = get("send_status_failed")
    val sendStatusSkipped: String get() = get("send_status_skipped")
    val sendTagField: String get() = get("send_tag_field")

    val catalogActionCollapse: String get() = get("catalog_action_collapse")
    val catalogActionExpand: String get() = get("catalog_action_expand")
    val catalogStatusCompleted: String get() = get("catalog_status_completed")
}

val LocalStrings = androidx.compose.runtime.staticCompositionLocalOf<AppStrings> {
    error("No AppStrings provided")
}

