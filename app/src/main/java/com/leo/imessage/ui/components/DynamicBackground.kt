package com.leo.imessage.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.leo.imessage.ui.theme.ChatBackground
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.LocalSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * The conversation wallpaper, still or moving.
 *
 * A dynamic background is a handful of wide, heavily blurred colour blobs
 * orbiting behind the base gradient at different speeds. Nothing has a hard
 * edge and nothing moves quickly, so it reads as depth behind the thread
 * rather than as something happening - which is the whole trick. Give it
 * sharp shapes or a visible period and it stops being a background and
 * starts being a distraction you have to look past to read a message.
 *
 * Cost is one full-screen draw per frame with no offscreen passes, and the
 * clock is read *inside* drawBehind so the animation never recomposes the
 * conversation on top of it. Reduce Motion freezes it to a still frame.
 */
@Composable
fun ChatWallpaper(
    background: ChatBackground,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    val base = background.brush

    if (!background.isDynamic || settings.lowPowerAnimations) {
        Box(
            modifier
                .fillMaxSize()
                .then(
                    if (base != null) Modifier.background(base)
                    else Modifier.background(palette.background)
                )
        ) {
            // A still frame of the drift, so a dynamic background under
            // Reduce Motion still looks like itself rather than a flat wash.
            if (background.isDynamic) {
                Box(Modifier.fillMaxSize().drawBehind { drawDrift(background.drift, 0.18f) })
            }
        }
        return
    }

    // One slow master clock; each blob derives its own phase from it, so
    // their periods never line up and the loop is impossible to spot.
    val clock = rememberInfiniteTransition(label = "wallpaper")
    val t = clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 46_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    Box(
        modifier
            .fillMaxSize()
            .then(if (base != null) Modifier.background(base) else Modifier)
            .drawBehind { drawDrift(background.drift, t.value) }
    )
}

/**
 * Draws the orbiting blobs.
 *
 * Each blob gets its own radius, orbit size and phase offset derived from
 * its index, and the radial gradient is taken well past the edge of the
 * screen so no blob ever shows a boundary.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrift(
    colors: List<Color>,
    phase: Float,
) {
    val tau = (Math.PI * 2).toFloat()
    colors.forEachIndexed { i, color ->
        // Coprime-ish multipliers keep the orbits out of sync.
        val speed = 1f + i * 0.37f
        val angle = (phase * speed + i * 0.61f) * tau
        val orbitX = size.width * (0.22f + i * 0.06f)
        val orbitY = size.height * (0.16f + i * 0.05f)
        val center = Offset(
            x = size.width * (0.3f + 0.4f * ((i % 2)).toFloat()) + cos(angle) * orbitX,
            y = size.height * (0.24f + 0.2f * i) + sin(angle * 0.8f) * orbitY,
        )
        val radius = size.minDimension * (0.62f + 0.12f * ((i % 3)).toFloat())

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.42f),
                    color.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}


/**
 * A background by id, worked out if it is one of the adaptive ones.
 *
 * The clock is re-read every few minutes so dusk arrives without relaunching
 * the app, and the weather is asked for on the same schedule the source
 * caches on. Anything that cannot be answered - no permission, no signal, no
 * last known location - falls back to the time of day, which needs nothing
 * and is never wrong.
 */
@Composable
fun rememberBackground(id: String): com.leo.imessage.ui.theme.ChatBackground {
    if (!com.leo.imessage.ui.theme.AdaptiveBackgrounds.isAdaptive(id)) {
        return androidx.compose.runtime.remember(id) {
            com.leo.imessage.ui.theme.backgroundById(id)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    val hour by androidx.compose.runtime.produceState(nowHour()) {
        while (true) {
            kotlinx.coroutines.delay(120_000)
            value = nowHour()
        }
    }

    val conditions by androidx.compose.runtime.produceState(
        com.leo.imessage.data.WeatherSource.lastKnown(),
        id,
    ) {
        if (id == com.leo.imessage.ui.theme.AdaptiveBackgrounds.TIME) return@produceState
        while (true) {
            // Keeps the previous answer on a failure rather than flickering
            // back to the fallback every time the network is briefly away.
            value = com.leo.imessage.data.WeatherSource.current(context) ?: value
            kotlinx.coroutines.delay(1_800_000)
        }
    }

    return androidx.compose.runtime.remember(id, hour, conditions) {
        val sky = conditions
        when {
            id == com.leo.imessage.ui.theme.AdaptiveBackgrounds.TIME || sky == null ->
                com.leo.imessage.ui.theme.timeBackground(hour)
            id == com.leo.imessage.ui.theme.AdaptiveBackgrounds.WEATHER ->
                com.leo.imessage.ui.theme.weatherBackground(sky)
            else -> com.leo.imessage.ui.theme.timeAndWeatherBackground(sky, hour)
        }
    }
}

private fun nowHour(): Int =
    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
