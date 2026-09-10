package com.leo.imessage.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    var swipeToReply by mutableStateOf(true)
    var showTimestampsOnSwipe by mutableStateOf(true)
    var groupByContact by mutableStateOf(true)
    var showUnreadBadges by mutableStateOf(true)
    var compactChatList by mutableStateOf(false)
    var sendWithReturn by mutableStateOf(false)
    var lowPowerAnimations by mutableStateOf(false)
}

val LocalSettings = staticCompositionLocalOf { AppSettings() }
