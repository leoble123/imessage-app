package com.leo.imessage.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

/**
 * The app's tint.
 *
 * Blue is iMessage's colour, not a law. Letting this move is the cheapest
 * way to stop the app looking like every other messaging client, because
 * the accent is on every button, link, receipt and reaction on screen.
 */
enum class AccentColor(val label: String, val light: Long, val dark: Long) {
    BLUE("Blue", 0xFF007AFF, 0xFF0A84FF),
    PURPLE("Purple", 0xFF8A3FFC, 0xFFA46BFF),
    PINK("Pink", 0xFFE0407F, 0xFFFF6FA5),
    ORANGE("Orange", 0xFFF06A0A, 0xFFFF9F45),
    GREEN("Green", 0xFF16A34A, 0xFF3ED67F),
    TEAL("Teal", 0xFF0E9BA8, 0xFF34D2E0),
    CRIMSON("Crimson", 0xFFD32F3F, 0xFFFF5A6A),
    GOLD("Gold", 0xFFB58900, 0xFFE9C24A),
}

/** How much a conversation row shows. */
enum class Density(val label: String) { COMPACT("Compact"), COMFORTABLE("Comfortable") }

/** What a double-tap on a bubble does. */
enum class DoubleTapAction(val label: String) {
    TAPBACK("Tapback"),
    REPLY("Reply"),
    DETAILS("Info"),
}

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
 * App-wide preferences.
 *
 * Each property writes through to disk as it changes, so a choice made once
 * stays made. The delegate pattern keeps that invisible to callers - screens
 * still just assign to `settings.themeMode` and never think about storage.
 */
class AppSettings(private val store: SettingsStore? = null) {

    private fun bool(key: String, default: Boolean) =
        object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
            private val state = mutableStateOf(store?.getBoolean(key, default) ?: default)
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(
                thisRef: Any?,
                property: kotlin.reflect.KProperty<*>,
                value: Boolean,
            ) {
                state.value = value
                store?.put(key, value)
            }
        }

    private inline fun <reified T : Enum<T>> enum(key: String, default: T) =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(
                store?.getString(key, default.name)
                    ?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() }
                    ?: default
            )
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
                state.value = value
                store?.put(key, value.name)
            }
        }

    private fun text(key: String, default: String) =
        object : kotlin.properties.ReadWriteProperty<Any?, String> {
            private val state = mutableStateOf(store?.getString(key, default) ?: default)
            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = state.value
            override fun setValue(
                thisRef: Any?,
                property: kotlin.reflect.KProperty<*>,
                value: String,
            ) {
                state.value = value
                store?.put(key, value)
            }
        }

    var themeMode by enum("theme_mode", ThemeMode.SYSTEM)
    var bubbleStyle by enum("bubble_style", BubbleStyle.GLASS)
    var sendReadReceipts by bool("read_receipts", true)
    var showTypingIndicators by bool("typing_indicators", true)
    var playEffects by bool("play_effects", true)
    var hapticsEnabled by bool("haptics", true)
    var motionProfile by enum("motion_profile", MotionProfile.BALANCED)
    var hapticProfile by enum("haptic_profile", HapticProfile.MEDIUM)
    var swipeToReply by bool("swipe_reply", true)
    var showTimestampsOnSwipe by bool("swipe_timestamps", true)
    var groupByContact by bool("group_by_contact", true)
    var showUnreadBadges by bool("unread_badges", true)
    var compactChatList by bool("compact_list", false)
    var sendWithReturn by bool("send_with_return", false)
    var lowPowerAnimations by bool("reduce_motion", false)

    /**
     * The wallpaper behind the conversation list.
     *
     * "none" is the flat theme background, which is the default and the one
     * that gets out of the way. The adaptive ones follow the clock, the
     * weather, or both.
     */
    var homeBackground by text("home_background", "none")


    /**
     * Gives every conversation its own colour, derived from who it's with.
     *
     * The honest reason this exists: iMessage is blue and grey and nothing
     * else, and after a while every thread looks like every other thread. A
     * per-chat hue means you can tell where you are from the corner of your
     * eye before you've read a word.
     */
    var colorfulBubbles by bool("colorful_bubbles", false)
    var accentColor by enum("accent_color", AccentColor.BLUE)
    var density by enum("density", Density.COMFORTABLE)
    var doubleTapAction by enum("double_tap", DoubleTapAction.TAPBACK)

    /**
     * Canned replies, kept as one newline-separated blob.
     *
     * A list of strings in SharedPreferences means a StringSet, which is
     * unordered - and the order of your own quick replies is the whole point,
     * since the one you use most should be first.
     */
    var templates: List<String>
        get() = templatesRaw.lines().filter { it.isNotBlank() }
        set(value) { templatesRaw = value.joinToString("\n") }

    private var templatesRaw by text(
        "templates",
        "On my way\nRunning a few minutes late\nCan I call you later?\nThanks!",
    )
    var notificationsEnabled by bool("notifications", true)

    // Account details. Nothing consumes these yet - the rustpush core will -
    // but they're real editable values rather than decorative rows.
    var appleAccount by text("apple_account", "")
    var relayServer by text("relay_server", "")
    var phoneNumber by text("phone_number", "")
}

val LocalSettings = staticCompositionLocalOf { AppSettings() }
