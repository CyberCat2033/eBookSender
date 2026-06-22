package com.cybercat.ebooksender.data.device

import com.cybercat.ebooksender.data.ftp.FtpEntry
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.ftp.FtpSessionInfo
import com.cybercat.ebooksender.data.pocketbook.PocketBookLibraryPaths
import com.cybercat.ebooksender.model.DeviceProfile
import com.cybercat.ebooksender.model.RemoteDevice
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DeviceProfileDetectorTest {

    private val device = RemoteDevice(host = "192.168.1.50", port = 2121)

    @Test
    fun detect_returnsPocketBookWhenDbExists() = runBlocking {
        val fakeFtp = FakeFtpGateway(
            listEntriesResult = Result.success(
                listOf(
                    FtpEntry(
                        name = PocketBookLibraryPaths.DATABASE_NAME,
                        path = "${PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY}/" +
                            PocketBookLibraryPaths.DATABASE_NAME,
                        isDirectory = false,
                        size = 1024L,
                        modifiedAtMillis = 1000L
                    )
                )
            )
        )
        val detector = DeviceProfileDetector(fakeFtp)
        val result = detector.detect(device)

        assertEquals(DeviceProfile.PocketBook, result.profile)
        assertEquals(PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY, fakeFtp.lastListEntriesPath)
    }

    @Test
    fun detect_returnsGenericFtpWhenDbMissing() = runBlocking {
        val fakeFtp = FakeFtpGateway(
            listEntriesResult = Result.success(
                listOf(
                    FtpEntry(
                        name = "some_other_file.txt",
                        path = "${PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY}/" +
                            "some_other_file.txt",
                        isDirectory = false,
                        size = 1024L,
                        modifiedAtMillis = 1000L
                    )
                )
            )
        )
        val detector = DeviceProfileDetector(fakeFtp)
        val result = detector.detect(device)

        assertEquals(DeviceProfile.GenericFtp, result.profile)
    }

    @Test
    fun detect_returnsGenericFtpOnFtpFailure() = runBlocking {
        val fakeFtp = FakeFtpGateway(
            listEntriesResult = Result.failure(RuntimeException("FTP connection lost"))
        )
        val detector = DeviceProfileDetector(fakeFtp)
        val result = detector.detect(device)

        assertEquals(DeviceProfile.GenericFtp, result.profile)
    }

    @Test
    fun detect_rethrowsCancellationException() = runBlocking {
        val fakeFtp = FakeFtpGateway(
            listEntriesResult = Result.success(emptyList()), // not used because we will throw
            shouldThrowCancellation = true
        )
        val detector = DeviceProfileDetector(fakeFtp)

        try {
            detector.detect(device)
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("coroutine cancelled", e.message)
        }
    }

    private class FakeFtpGateway(
        private val listEntriesResult: Result<List<FtpEntry>>,
        private val shouldThrowCancellation: Boolean = false
    ) : FtpGateway {
        var lastListEntriesPath: String? = null

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
        ): Result<List<FtpEntry>> {
            if (shouldThrowCancellation) {
                throw CancellationException("coroutine cancelled")
            }
            lastListEntriesPath = remoteRelativePath
            return listEntriesResult
        }

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
        ): Result<Unit> = error("Unused in test")
    }
}
