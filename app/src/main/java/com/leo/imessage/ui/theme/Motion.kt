package com.leo.imessage.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion specs tuned to feel like UIKit rather than Material.
 *
 * Everything interactive is a spring with visible overshoot. Damping ratios
 * here are deliberately lower than Compose's defaults - Material's springs
 * settle without ever crossing their target, which reads as "smooth but
 * dead". iOS lets things overshoot and come back, and that little rebound is
 * most of what people mean by "bouncy".
 */
object Motion {
    /** Standard UI spring. Overshoots slightly, settles fast. */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.68f,
        stiffness = 340f,
    )

    /** Direct-manipulation follow-through (drags, swipes). Barely overshoots. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = 620f,
    )

    /** Things that should feel alive - bubble send, tapbacks, buttons. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.42f,
        stiffness = 480f,
    )

    /** Maximum personality, for one-shot celebratory moments. */
    fun <T> springy() = spring<T>(
        dampingRatio = 0.34f,
        stiffness = 420f,
    )

    /** Large surfaces moving (sheet presentation). Soft, no bounce. */
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.9f,
        stiffness = 260f,
    )

    /** iOS's standard ease curve, for pure opacity/color changes. */
    val AppleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> fade(durationMillis: Int = 200) = tween<T>(
        durationMillis = durationMillis,
        easing = AppleEase,
    )
}
