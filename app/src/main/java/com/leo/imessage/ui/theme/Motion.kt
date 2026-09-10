package com.leo.imessage.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion specs tuned to UIKit.
 *
 * The thing that reads as "buttery" on iOS is not bounce - it's that
 * animations are *fast* and *well damped*: they arrive quickly, overshoot by
 * a hair, and stop. A low damping ratio wobbles, and a wobble reads as cheap
 * and jolty rather than lively. So damping sits high here (0.8-1.0) and
 * liveliness comes from stiffness instead, with real overshoot reserved for
 * the few moments that should feel physical.
 *
 * For reference, SwiftUI's default `.spring()` is roughly dampingFraction
 * 0.825 - notably tame. These match that neighbourhood.
 */
object Motion {
    /** Default for view transitions. Arrives fast, barely overshoots. */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.88f,
        stiffness = 420f,
    )

    /** Follows a finger (drags, swipes, scrubbing). No perceptible bounce. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.95f,
        stiffness = 900f,
    )

    /**
     * Small controls that should feel physical when tapped - send button,
     * tapbacks. Overshoots visibly but settles in one pass, no wobble.
     */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = 650f,
    )

    /** Large surfaces (sheets, modals). Critically damped - never bounces. */
    fun <T> gentle() = spring<T>(
        dampingRatio = 1f,
        stiffness = 340f,
    )

    /**
     * Touch-down feedback on a large target (a bubble, a row).
     *
     * Slower and softer than [snappy] on purpose: a press that snaps in
     * 80ms only ever paints two or three frames, and three frames of scale
     * change reads as a step rather than a glide no matter what the panel is
     * doing. Stretching it to roughly a quarter second gives the spring
     * enough frames to actually look continuous.
     */
    fun <T> pressIn() = spring<T>(
        dampingRatio = 0.85f,
        stiffness = 700f,
    )

    /** iOS's standard ease curve, for opacity and color. */
    val AppleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> fade(durationMillis: Int = 180) = tween<T>(
        durationMillis = durationMillis,
        easing = AppleEase,
    )
}
