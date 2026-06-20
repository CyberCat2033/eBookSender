package com.cybercat.ebooksender.transfer

import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.di.ApplicationScope
import com.cybercat.ebooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class FtpConnectionHealthMonitor @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val connectionManager: ConnectionManager,
    private val ftpGateway: FtpGateway,
    private val transferCoordinator: TransferCoordinator
) {
    private var monitorJob: Job? = null

    @Synchronized
    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = applicationScope.launch {
            connectionManager.connectedDevice
                .collectLatest { device ->
                    if (device != null) {
                        monitorConnection(device)
                    }
                }
        }
    }

    private suspend fun monitorConnection(device: RemoteDevice) {
        delay(INITIAL_HEALTH_CHECK_DELAY_MS)

        var failedChecks = 0
        while (connectionManager.connectedDevice.value == device) {
            if (transferCoordinator.hasActiveTransfer()) {
                failedChecks = 0
                delay(HEALTH_CHECK_INTERVAL_MS)
                continue
            }

            val result = ftpGateway.checkConnection(device)
            if (result.isSuccess) {
                failedChecks = 0
                delay(HEALTH_CHECK_INTERVAL_MS)
            } else {
                failedChecks += 1
                if (failedChecks >= DISCONNECT_AFTER_FAILED_CHECKS) {
                    connectionManager.disconnectIfCurrent(device)
                    return
                }
                delay(FAILED_RECHECK_DELAY_MS)
            }
        }
    }

    private companion object {
        const val INITIAL_HEALTH_CHECK_DELAY_MS = 15_000L
        const val HEALTH_CHECK_INTERVAL_MS = 15_000L
        const val FAILED_RECHECK_DELAY_MS = 5_000L
        const val DISCONNECT_AFTER_FAILED_CHECKS = 2
    }
}
