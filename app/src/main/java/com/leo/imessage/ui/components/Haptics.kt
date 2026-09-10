package com.leo.imessage.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.leo.imessage.ui.theme.LocalSettings

/**
 * Haptics that respect the Haptic Feedback setting.
 *
 * Every gesture in the app taps the motor directly, which meant the setting
 * existed but did nothing. Rather than threading a check through several
 * dozen call sites, this wraps the platform implementation once and every
 * site keeps the code it already had.
 */
@Composable
fun rememberHaptics(): HapticFeedback {
    val view = androidx.compose.ui.platform.LocalView.current
    val settings = LocalSettings.current

    return remember(view, settings) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                // Read at call time, so changing the profile takes effect at
                // once rather than on the next recomposition of each caller.
                if (!settings.hapticsEnabled) return
                val constant = when (settings.hapticProfile) {
                    com.leo.imessage.ui.theme.HapticProfile.OFF -> return
                    // Compose's own HapticFeedback only exposes two strengths,
                    // which is why the setting had nothing to vary. Going
                    // through the View reaches the platform's full set.
                    com.leo.imessage.ui.theme.HapticProfile.SOFT ->
                        android.view.HapticFeedbackConstants.CLOCK_TICK
                    com.leo.imessage.ui.theme.HapticProfile.LIGHT ->
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP
                    com.leo.imessage.ui.theme.HapticProfile.MEDIUM ->
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK
                    com.leo.imessage.ui.theme.HapticProfile.STRONG ->
                        android.view.HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(
                    constant,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
            }
        }
    }
}
