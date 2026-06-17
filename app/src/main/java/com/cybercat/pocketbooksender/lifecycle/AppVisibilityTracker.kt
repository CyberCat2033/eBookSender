package com.cybercat.pocketbooksender.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

internal object AppVisibilityTracker : DefaultLifecycleObserver {
    private val appVisible = AtomicBoolean(false)

    val isAppVisible: Boolean
        get() = appVisible.get()

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        appVisible.set(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        appVisible.set(false)
    }
}
