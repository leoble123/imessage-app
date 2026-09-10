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
    val platform = LocalHapticFeedback.current
    val settings = LocalSettings.current
    return remember(platform, settings) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                // Read at call time, so flipping the switch takes effect at
                // once rather than on the next recomposition of each caller.
                if (settings.hapticsEnabled) {
                    platform.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
}
