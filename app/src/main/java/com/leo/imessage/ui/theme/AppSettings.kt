package com.leo.imessage.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How much the interface moves.
 *
 * MINIMAL isn't "no animation" - it's the same animations run short enough
 * to read as instant, which keeps state changes explicable without making
 * anyone wait for them.
 */
enum class MotionProfile(val label: String, val scale: Float) {
    MINIMAL("Minimal", 0.62f),
    BALANCED("Balanced", 1f),
    EXPRESSIVE("Expressive", 1.28f),
}

/** Strength of touch feedback, mapped onto the platform's haptic constants. */
enum class HapticProfile(val label: String) {
    OFF("Off"),
    SOFT("Soft"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong"),
}

/**
 * GLASS is the iOS 26 material - translucent, lit rim, specular sheen.
 * GRADIENT and FLAT are the older opaque looks, kept as options.
 */
enum class BubbleStyle { GLASS, GRADIENT, FLAT }

/**
 * App-wide preferences, held in memory for now.
 *
 * Deliberately not persisted yet: once the rustpush backend lands there will
 * be a real settings store to write into, and having two is worse than
 * having none.
 */
class AppSettings {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var bubbleStyle by mutableStateOf(BubbleStyle.GLASS)
    var sendReadReceipts by mutableStateOf(true)
    var showTypingIndicators by mutableStateOf(true)
    var playEffects by mutableStateOf(true)
    var hapticsEnabled by mutableStateOf(true)
    var motionProfile by mutableStateOf(MotionProfile.BALANCED)
    var hapticProfile by mutableStateOf(HapticProfile.MEDIUM)
    var swipeToReply by mutableStateOf(true)
    var showTimestampsOnSwipe by mutableStateOf(true)
    var groupByContact by mutableStateOf(true)
    var showUnreadBadges by mutableStateOf(true)
    var compactChatList by mutableStateOf(false)
    var sendWithReturn by mutableStateOf(false)
    var lowPowerAnimations by mutableStateOf(false)

    // Account details. Nothing consumes these yet - the rustpush core will -
    // but they're real editable values rather than decorative rows.
    var appleAccount by mutableStateOf("")
    var relayServer by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
}

val LocalSettings = staticCompositionLocalOf { AppSettings() }
