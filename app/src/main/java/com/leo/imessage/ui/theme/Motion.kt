package com.leo.imessage.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion specs tuned to feel like UIKit rather than Material.
 *
 * The single biggest tell between an Android app and an iOS one is that
 * Material leans on duration+easing curves while UIKit leans on springs with
 * a little overshoot. Everything interactive here uses a spring; only
 * non-physical things (opacity crossfades) use a curve.
 */
object Motion {
    /** Standard UI spring - what most iOS view transitions feel like. */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 380f,
    )

    /** Snappier, for direct-manipulation follow-through (drags, swipes). */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = 700f,
    )

    /** Bouncier, for things that should feel alive (bubble send, tapbacks). */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.58f,
        stiffness = 520f,
    )

    /** Very soft, for large surfaces moving (sheet presentation). */
    fun <T> gentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 240f,
    )

    /** iOS's standard ease curve, for pure opacity/color changes. */
    val AppleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> fade(durationMillis: Int = 200) = tween<T>(
        durationMillis = durationMillis,
        easing = AppleEase,
    )

    /** Duration of the push/pop navigation transition, matching UIKit. */
    const val NavTransitionMillis = 350
}
