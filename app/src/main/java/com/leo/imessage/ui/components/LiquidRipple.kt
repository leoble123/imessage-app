package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalSettings
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * A ring of light that spreads from wherever you touched.
 *
 * Not Material's ripple, which fills a shape with a flat wash and stops at
 * its bounds. This is the surface of water taking a hit: a thin ring that
 * expands, thins as it grows, and is gone in under half a second - so it
 * reads as the touch *landing on something* rather than as a highlight.
 *
 * Watched on the Initial pass so it fires the instant a finger arrives,
 * before any child decides what the gesture was for, and it never consumes -
 * the ring is feedback, not a handler, and stealing the event would break
 * every tap it decorates.
 */
fun Modifier.liquidRipple(
    color: Color? = null,
    maxRadius: Dp = 120.dp,
    durationMillis: Int = 520,
): Modifier = composed {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    val tint = color ?: com.leo.imessage.ui.theme.LocalPalette.current.accent

    // Reduce Motion turns this off outright - it is pure decoration, and
    // decoration is the first thing that should go when someone asks for less.
    if (settings.lowPowerAnimations) return@composed this

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                origin = down.position
                scope.launch {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(durationMillis, easing = Motion.AppleEase))
                }
            }
        }
        .drawWithContent {
            drawContent()
            val p = progress.value
            if (p <= 0f || p >= 1f || origin == Offset.Unspecified) return@drawWithContent

            val radius = maxRadius.toPx() * p
            // Thins and fades as it spreads, the way a real ring does when
            // the same energy is stretched around a longer circumference.
            val fade = (1f - p) * (1f - p) * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        tint.copy(alpha = fade),
                        Color.Transparent,
                    ),
                    center = origin,
                    radius = radius.coerceAtLeast(1f),
                ),
                radius = radius.coerceAtLeast(1f),
                center = origin,
            )
        }
}

/**
 * The ripple a sent message leaves behind.
 *
 * Fires once when a bubble first appears rather than on touch, so the
 * transcript reacts to the message landing in it.
 */
@Composable
fun rememberArrivalRipple(key: Any): Animatable<Float, *> {
    val progress = remember(key) { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) {
        progress.animateTo(1f, tween(620, easing = Motion.AppleEase))
    }
    return progress
}
