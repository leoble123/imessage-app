package com.leo.imessage.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion specs, expressed the way Apple expresses them.
 *
 * SwiftUI doesn't describe a spring by stiffness - it uses *response* (how
 * long the spring takes to travel, in seconds) and *damping fraction* (how
 * much it overshoots). Its default is response 0.55 / damping 0.825, and its
 * three presets - smooth, snappy, bouncy - all sit at a 0.5s response and
 * differ only in bounce.
 *
 * That's the number that matters here: every spring in this app used to run
 * at a 0.21-0.34s response, which is two to three times faster than
 * anything iOS ships. Fast springs don't read as responsive, they read as
 * *abrupt* - the motion is over before the eye can follow it, so what
 * registers isn't a movement but a jump. Slowing the response down and
 * keeping damping high is what turns it liquid.
 */
object Motion {
    /**
     * Global multiplier on every spring's response, driven by the Motion
     * profile in Settings.
     *
     * One dial rather than a per-animation setting: the whole point of a
     * motion profile is that the app moves as one thing. Scaling response
     * keeps every spring's *character* - what overshoots still overshoots -
     * and only changes how long it all takes.
     */
    private val responseScaleState = androidx.compose.runtime.mutableFloatStateOf(1f)
    var responseScale: Float
        get() = responseScaleState.floatValue
        set(value) {
            if (responseScaleState.floatValue != value) responseScaleState.floatValue = value
        }

    /** 2pi/response squared, since Compose takes stiffness with unit mass. */
    private fun stiffnessFor(response: Float): Float {
        val omega = (2.0 * Math.PI / (response * responseScale)).toFloat()
        return omega * omega
    }

    private fun <T> springOf(response: Float, damping: Float) = spring<T>(
        dampingRatio = damping,
        stiffness = stiffnessFor(response),
    )

    /** Default for view transitions. SwiftUI's `.smooth`, near enough. */
    fun <T> standard() = springOf<T>(response = 0.5f, damping = 0.92f)

    /**
     * Settles a gesture back into place. The one spec that stays quick,
     * because it's resolving motion the finger already started - but still
     * well off the 0.21s it used to sit at.
     */
    fun <T> snappy() = springOf<T>(response = 0.4f, damping = 0.9f)

    /** Things that should feel physical - send, tapbacks. SwiftUI `.bouncy`. */
    fun <T> bouncy() = springOf<T>(response = 0.55f, damping = 0.76f)

    /** Large surfaces (sheets, modals). Critically damped - never bounces. */
    fun <T> gentle() = springOf<T>(response = 0.62f, damping = 1f)

    /**
     * Touch-down feedback on a large target (a bubble, a row).
     *
     * Slow and heavily damped: a press that lands in 80ms only ever paints
     * two or three frames, and three frames of scale change reads as a step
     * rather than a glide no matter what the panel is doing.
     */
    fun <T> pressIn() = springOf<T>(response = 0.5f, damping = 0.96f)

    /** SwiftUI's untouched default, for anything that just wants to flow. */
    fun <T> fluid() = springOf<T>(response = 0.55f, damping = 0.825f)

    /** iOS's standard ease curve, for opacity and color. */
    val AppleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> fade(durationMillis: Int = 260) = tween<T>(
        durationMillis = durationMillis,
        easing = AppleEase,
    )
}
