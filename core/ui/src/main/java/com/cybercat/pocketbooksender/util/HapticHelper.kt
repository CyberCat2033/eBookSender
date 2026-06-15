package com.cybercat.pocketbooksender.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Helper object/extensions to play haptic feedback conforming to Material 3 / Material You guidelines,
 * respecting the user setting and Do Not Disturb (DND) / Silent modes.
 */
object HapticHelper {

    /**
     * Checks if Do Not Disturb (DND) mode is currently active.
     */
    fun isDoNotDisturbActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return if (nm != null) {
            val filter = nm.currentInterruptionFilter
            filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } else {
            false
        }
    }

    /**
     * Checks if the device is in Silent mode.
     */
    fun isSilentModeActive(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audio?.ringerMode == AudioManager.RINGER_MODE_SILENT
    }
}

/**
 * Extension function on View to perform haptic feedback safely.
 * Respects app-level setting, system DND (Do Not Disturb), and Silent mode.
 */
fun View.performHapticIfAllowed(
    context: Context,
    enableHaptics: Boolean,
    feedbackConstant: Int = HapticFeedbackConstants.VIRTUAL_KEY,
    ignoreDnd: Boolean = false
) {
    if (!enableHaptics) return
    if (!ignoreDnd && HapticHelper.isDoNotDisturbActive(context)) return
    if (!ignoreDnd && HapticHelper.isSilentModeActive(context)) return
    
    performHapticFeedback(feedbackConstant)
}
