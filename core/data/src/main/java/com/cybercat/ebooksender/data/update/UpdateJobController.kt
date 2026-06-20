package com.cybercat.ebooksender.data.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdateJobController<State>(
    private val scope: CoroutineScope,
    private val state: StateFlow<State>,
    private val updateState: ((State) -> State) -> Unit,
    private val statusEventId: (State) -> Long,
    private val clearStatus: (State) -> State,
    private val statusAutoClearDelayMs: Long
) {
    private var checkJob: Job? = null
    private var installJob: Job? = null
    private var statusClearJob: Job? = null

    fun launchCheck(block: suspend CoroutineScope.() -> Unit): Boolean {
        if (checkJob?.isActive == true) return false
        checkJob = scope.launch(block = block)
        return true
    }

    fun launchInstall(block: suspend CoroutineScope.() -> Unit): Boolean {
        if (installJob?.isActive == true) return false
        installJob = scope.launch(block = block)
        return true
    }

    fun cancelInstall() {
        installJob?.cancel()
    }

    fun cancelActiveInstallIfPresent(onBeforeCancel: () -> Unit): Boolean {
        val job = installJob?.takeIf { it.isActive } ?: return false
        onBeforeCancel()
        job.cancel()
        return true
    }

    fun clearStatus() {
        statusClearJob?.cancel()
        updateState(clearStatus)
    }

    fun scheduleStatusClearIf(shouldClear: Boolean) {
        if (!shouldClear) return

        statusClearJob?.cancel()
        val eventId = statusEventId(state.value)
        statusClearJob = scope.launch {
            delay(statusAutoClearDelayMs)
            updateState { currentState ->
                if (statusEventId(currentState) == eventId) {
                    clearStatus(currentState)
                } else {
                    currentState
                }
            }
        }
    }
}
