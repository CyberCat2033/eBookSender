package com.cybercat.ebooksender.data.settings

import com.cybercat.ebooksender.data.device.DeviceLibraryRefresher
import com.cybercat.ebooksender.data.ftp.FtpEntry
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.ftp.FtpReplyException
import com.cybercat.ebooksender.data.ftp.FtpSessionInfo
import com.cybercat.ebooksender.model.FolderRenameMethod
import com.cybercat.ebooksender.model.RemoteDevice
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFolderRenameUseCaseTest {

    private val oldName = "OldFolder"
    private val newName = "NewFolder"

    @Test
    fun renameFolder_notSupportedByDeviceProfile() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.None
            )
        val ftpGateway = FakeFtpGateway(Result.success(Unit))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertEquals(DeviceFolderRenameUseCase.Result.NotSupported, result)
        assertFalse(ftpGateway.renameCalled)
        assertFalse(libraryRefresher.refreshCalled)
    }

    @Test
    fun renameFolder_success() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.FtpOnly
            )
        val ftpGateway = FakeFtpGateway(Result.success(Unit))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertEquals(DeviceFolderRenameUseCase.Result.Success, result)
        assertTrue(ftpGateway.renameCalled)
        assertEquals(oldName, ftpGateway.lastFromPath)
        assertEquals(newName, ftpGateway.lastToPath)
        assertTrue(libraryRefresher.refreshCalled)
    }

    @Test
    fun renameFolder_ftpReply500_notSupported() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.FtpOnly
            )
        val ftpException = FtpReplyException(500, "Syntax error", "Command not recognized")
        val ftpGateway = FakeFtpGateway(Result.failure(ftpException))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertEquals(DeviceFolderRenameUseCase.Result.NotSupported, result)
        assertTrue(ftpGateway.renameCalled)
        assertFalse(libraryRefresher.refreshCalled)
    }

    @Test
    fun renameFolder_ftpReply502_notSupported() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.FtpOnly
            )
        val ftpException =
            FtpReplyException(502, "Command not implemented", "Rename is not implemented")
        val ftpGateway = FakeFtpGateway(Result.failure(ftpException))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertEquals(DeviceFolderRenameUseCase.Result.NotSupported, result)
        assertTrue(ftpGateway.renameCalled)
        assertFalse(libraryRefresher.refreshCalled)
    }

    @Test
    fun renameFolder_ftpReply550_alreadyExists() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.FtpOnly
            )
        val ftpException = FtpReplyException(550, "File exists", "Requested action not taken")
        val ftpGateway = FakeFtpGateway(Result.failure(ftpException))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertEquals(DeviceFolderRenameUseCase.Result.AlreadyExists, result)
        assertTrue(ftpGateway.renameCalled)
        assertFalse(libraryRefresher.refreshCalled)
    }

    @Test
    fun renameFolder_genericError() = runBlocking {
        val device =
            RemoteDevice(
                host = "192.168.1.50",
                port = 2121,
                supportsFolderRename = FolderRenameMethod.FtpOnly
            )
        val exception = RuntimeException("Connection timed out")
        val ftpGateway = FakeFtpGateway(Result.failure(exception))
        val libraryRefresher = FakeDeviceLibraryRefresher()
        val useCase = DeviceFolderRenameUseCase(ftpGateway, libraryRefresher)

        val result = useCase.renameFolder(device, oldName, newName)

        assertTrue(result is DeviceFolderRenameUseCase.Result.Error)
        assertEquals(
            "Connection timed out",
            (result as DeviceFolderRenameUseCase.Result.Error).message
        )
        assertTrue(ftpGateway.renameCalled)
        assertFalse(libraryRefresher.refreshCalled)
    }

    private class FakeFtpGateway(private val renameResult: Result<Unit>) : FtpGateway {
        var renameCalled = false
        var lastFromPath: String? = null
        var lastToPath: String? = null

        override suspend fun checkConnection(device: RemoteDevice): Result<FtpSessionInfo> =
            error("Unused in test")

        override suspend fun uploadAtomically(
            device: RemoteDevice,
            remoteRelativePath: String,
            input: InputStream,
            onProgress: ((Long) -> Unit)?
        ): Result<Unit> = error("Unused in test")

        override suspend fun listDirectories(
            device: RemoteDevice,
            remoteRelativePath: String
        ): Result<List<String>> = error("Unused in test")

        override suspend fun listEntries(
            device: RemoteDevice,
            remoteRelativePath: String
        ): Result<List<FtpEntry>> = error("Unused in test")

        override suspend fun downloadFile(
            device: RemoteDevice,
            remoteRelativePath: String,
            output: OutputStream
        ): Result<Unit> = error("Unused in test")

        override suspend fun deleteFile(
            device: RemoteDevice,
            remoteRelativePath: String
        ): Result<Unit> = error("Unused in test")

        override suspend fun deleteDirectory(
            device: RemoteDevice,
            remoteRelativePath: String
        ): Result<Unit> = error("Unused in test")

        override suspend fun rename(
            device: RemoteDevice,
            fromPath: String,
            toPath: String
        ): Result<Unit> {
            renameCalled = true
            lastFromPath = fromPath
            lastToPath = toPath
            return renameResult
        }
    }

    private class FakeDeviceLibraryRefresher : DeviceLibraryRefresher(null, true) {
        var refreshCalled = false

        override suspend fun refreshAndWait(device: RemoteDevice): Result<Unit> {
            refreshCalled = true
            return Result.success(Unit)
        }
    }
}
