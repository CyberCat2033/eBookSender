package com.cybercat.pocketbooksender.data.catalog

import com.cybercat.pocketbooksender.data.ftp.FtpEntry
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.domain.AllSupportedExtensions
import com.cybercat.pocketbooksender.domain.MangaArchiveExtensions
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DeviceCatalogRepository @Inject constructor(
    private val ftpGateway: FtpGateway,
    private val databaseReader: PocketBookDatabaseReader,
    private val catalogTreeBuilder: CatalogTreeBuilder,
    private val connectionManager: ConnectionManager,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _catalog = MutableStateFlow(DeviceCatalog())
    val catalog: StateFlow<DeviceCatalog> = _catalog.asStateFlow()

    private val deletedPaths = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val loadMutex = Mutex()

    init {
        connectionManager.connectedDevice
            .onEach { device ->
                if (device != null) {
                    refresh(device)
                } else {
                    clear()
                }
            }
            .launchIn(scope)

        settingsRepository.settings
            .map { it.rootPath to (it.booksFolderName + "/" + it.documentsFolderName + "/" + it.mangaFolderName) }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                deletedPaths.clear()
                val device = connectionManager.connectedDevice.value
                if (device != null) {
                    refresh(device)
                }
            }
            .launchIn(scope)
    }

    suspend fun refresh(device: PocketBookDevice) {
        _catalog.update { it.copy(isLoading = true) }
        val settings = settingsRepository.settings.first()
        val result = loadMutex.withLock {
            runCatching { load(device, settings) }
        }
        val loaded = result.getOrDefault(DeviceCatalog(errorMessage = "Load failed"))
        _catalog.value = loaded.filterDeleted()
    }

    fun clear() {
        deletedPaths.clear()
        _catalog.value = DeviceCatalog()
    }

    private fun DeviceCatalog.filterDeleted(): DeviceCatalog {
        val deleted = deletedPaths.toSet()
        if (deleted.isEmpty()) return this
        return copy(
            books = books.mapNotNull { group ->
                val files = group.files.filterNot { it.path in deleted }
                group.copy(files = files).takeIf { it.files.isNotEmpty() }
            },
            documents = documents.mapNotNull { group ->
                val files = group.files.filterNot { it.path in deleted }
                group.copy(files = files).takeIf { it.files.isNotEmpty() }
            },
            manga = manga.mapNotNull { group ->
                val files = group.files.filterNot { it.path in deleted }
                group.copy(
                    files = files,
                    latestFile = group.latestFile?.takeIf { it.path !in deleted } ?: files.lastOrNull(),
                    lastReadFile = group.lastReadFile?.takeIf { it.path !in deleted } ?: files.lastReadFile(),
                ).takeIf { it.files.isNotEmpty() }
            },
        )
    }

    suspend fun deleteFiles(device: PocketBookDevice, paths: List<String>) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val safePaths = paths.map { validateCatalogDeletePath(it, settings) }.distinct()
        if (safePaths.isEmpty()) return@withContext

        val selectedPathSet = safePaths.toSet()
        val folderCandidates = _catalog.value.deleteFolderCandidates(selectedPathSet)
        var firstError: Throwable? = null
        val successfulPaths = mutableListOf<String>()
        safePaths.forEach { path ->
            ftpGateway.deleteFile(device, path)
                .onSuccess {
                    successfulPaths.add(path)
                }
                .onFailure { error ->
                    if (firstError == null) firstError = error
                }
        }

        val successfulPathSet = successfulPaths.toSet()
        val folderPaths = folderCandidates
            .filter { candidate -> candidate.filePaths.all { it in successfulPathSet } }
            .map { candidate -> validateCatalogDeleteFolderPath(candidate.path, settings) }
            .distinct()
            .sortedByDescending { path -> path.count { it == '/' } }

        folderPaths.forEach { folderPath ->
            ftpGateway.deleteDirectory(device, folderPath)
                .onFailure { error ->
                    if (firstError == null) firstError = error
                }
        }

        deletedPaths.addAll(successfulPaths)
        refresh(device)
        firstError?.let { throw it }
    }

    private fun validateCatalogDeletePath(path: String, settings: AppSettings): String {
        val trimmed = path.replace('\\', '/').trim()
        require(trimmed.isNotBlank()) { "Cannot delete an empty catalog path" }
        require(!trimmed.startsWith("/")) { "Unsafe catalog file path" }

        val segments = trimmed
            .split('/')
            .filter { it.isNotBlank() }
        require(segments.none { it == "." || it == ".." }) { "Unsafe catalog file path" }

        val normalized = segments.joinToString("/")
        require(
            normalized.isUnder(settings.booksFolderName) ||
                normalized.isUnder(settings.documentsFolderName) ||
                normalized.isUnder(settings.mangaFolderName)
        ) {
            "Catalog deletion is limited to ${settings.booksFolderName}, ${settings.documentsFolderName}, and ${settings.mangaFolderName}"
        }
        require(normalized.contentExtension() in AllSupportedExtensions) {
            "Unsupported catalog file type"
        }
        return normalized
    }

    private fun validateCatalogDeleteFolderPath(path: String, settings: AppSettings): String {
        val trimmed = path.replace('\\', '/').trim()
        require(trimmed.isNotBlank()) { "Cannot delete an empty catalog folder path" }
        require(!trimmed.startsWith("/")) { "Unsafe catalog folder path" }

        val segments = trimmed
            .split('/')
            .filter { it.isNotBlank() }
        require(segments.none { it == "." || it == ".." }) { "Unsafe catalog folder path" }
        require(segments.size >= 2) { "Refusing to delete catalog root folder" }

        val normalized = segments.joinToString("/")
        require(
            normalized.isUnder(settings.booksFolderName) ||
                normalized.isUnder(settings.documentsFolderName) ||
                normalized.isUnder(settings.mangaFolderName)
        ) {
            "Catalog folder deletion is limited to ${settings.booksFolderName}, ${settings.documentsFolderName}, and ${settings.mangaFolderName}"
        }
        return normalized
    }

    private fun DeviceCatalog.deleteFolderCandidates(
        selectedPaths: Set<String>,
    ): List<CatalogDeleteFolderCandidate> = buildList {
        fun addCandidate(groupPath: String, files: List<CatalogFile>) {
            if ('/' !in groupPath.trim('/')) return
            val filePaths = files.mapTo(mutableSetOf()) { it.path }
            if (filePaths.isEmpty() || !selectedPaths.containsAll(filePaths)) return
            if (files.any { file -> !file.path.isUnderFolder(groupPath) }) return

            add(
                CatalogDeleteFolderCandidate(
                    path = groupPath,
                    filePaths = filePaths,
                )
            )
        }

        books.forEach { group -> addCandidate(group.path, group.files) }
        documents.forEach { group -> addCandidate(group.path, group.files) }
        manga.forEach { group -> addCandidate(group.path, group.files) }
    }

    suspend fun load(device: PocketBookDevice, settings: AppSettings): DeviceCatalog = withContext(Dispatchers.IO) {
        val databaseCatalog = runCatching { loadFromPocketBookDatabase(device, settings) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
        if (databaseCatalog != null && !databaseCatalog.isEmpty) {
            databaseCatalog
        } else {
            loadFromFolders(device, settings)
        }
    }

    private suspend fun loadFromPocketBookDatabase(device: PocketBookDevice, settings: AppSettings): DeviceCatalog {
        return catalogTreeBuilder.buildFromDatabaseFiles(
            files = databaseReader.readCatalogFiles(device, settings),
            settings = settings,
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun loadFromFolders(device: PocketBookDevice, settings: AppSettings): DeviceCatalog =
        DeviceCatalog(
            books = loadGroupedFiles(device, settings.booksFolderName, settings),
            documents = loadGroupedFiles(device, settings.documentsFolderName, settings),
            manga = loadMangaSeries(device, settings.mangaFolderName, settings),
            scannedAtMillis = System.currentTimeMillis(),
            isLoading = false,
            errorMessage = null,
        )

    private suspend fun loadGroupedFiles(
        device: PocketBookDevice,
        root: String,
        settings: AppSettings,
    ): List<CatalogGroup> {
        val rootEntries = ftpGateway.listEntries(device, root).getOrThrow()
        val rootFiles = rootEntries
            .filterNot(FtpEntry::isDirectory)
            .filter { it.isKnownBookFile() }
            .map { it.toCatalogFile() }

        val folderGroups = rootEntries
            .filter(FtpEntry::isDirectory)
            .mapNotNull { group ->
                val files = ftpGateway.listEntries(device, group.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.isKnownBookFile() }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.path })

                if (files.isEmpty()) {
                    null
                } else {
                    CatalogGroup(
                        name = group.name,
                        path = group.path,
                        files = files,
                    )
                }
            }

        val fallbackGroup = if (rootFiles.isEmpty()) {
            null
        } else {
            CatalogGroup(
                name = if (root == settings.documentsFolderName) "Untagged" else "Unknown Author",
                path = root,
                files = rootFiles.sortedWith(NaturalSort.by { it.path }),
            )
        }

        return (listOfNotNull(fallbackGroup) + folderGroups)
            .sortedWith(NaturalSort.by { it.name })
    }

    private suspend fun loadMangaSeries(
        device: PocketBookDevice,
        root: String,
        settings: AppSettings,
    ): List<MangaSeriesGroup> {
        return ftpGateway.listEntries(device, root)
            .getOrThrow()
            .filter(FtpEntry::isDirectory)
            .mapNotNull { series ->
                val files = ftpGateway.listEntries(device, series.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.name.contentExtension() in MangaArchiveExtensions }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.path })

                if (files.isEmpty()) {
                    null
                } else {
                    MangaSeriesGroup(
                        name = series.name,
                        path = series.path,
                        latestFile = files.lastOrNull(),
                        lastReadFile = null,
                        files = files,
                    )
                }
            }
            .sortedWith(NaturalSort.by { it.name })
    }

    private fun FtpEntry.toCatalogFile(): CatalogFile =
        CatalogFile(
            name = name,
            path = path,
            size = size,
            modifiedAtMillis = modifiedAtMillis,
        )

    private fun FtpEntry.isKnownBookFile(): Boolean =
        name.contentExtension() in AllSupportedExtensions

    private data class CatalogDeleteFolderCandidate(
        val path: String,
        val filePaths: Set<String>,
    )
}
