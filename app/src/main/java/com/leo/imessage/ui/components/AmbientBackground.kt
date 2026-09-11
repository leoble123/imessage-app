package com.leo.imessage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.LocalSettings
import com.leo.imessage.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** How long one full drift takes. */
private const val CYCLE_MS = 70_000f

/** And how often it is nudged along. */
private const val STEP_MS = 125L

/**
 * The slow colour field the conversation list floats on.
 *
 * The list used to sit on a flat fill, which is most of why it read as a
 * ledger - and it wasted the one thing this app has that a stock messenger
 * doesn't: every bar on this screen is a real backdrop blur, and a blur of a
 * flat colour is just that colour. Give it something moving to sample and the
 * glass finally behaves like glass.
 *
 * Four soft blobs on long, mutually-prime orbits, drawn well outside the
 * bounds so no edge is ever visible - what you see is the overlap drifting,
 * never a shape crossing the screen. It is kept far below the threshold where
 * it would compete with the text: this has to survive being looked at every
 * day, and anything you notice on the second day is too strong.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    val oled = settings.themeMode == ThemeMode.OLED

    // Hue-rotations of the accent rather than fixed colours, so the field
    // belongs to whatever accent is set instead of fighting it.
    val colors = remember(palette.accent, palette.isDark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(palette.accent.toArgb(), hsv)
        listOf(-38f, 26f, 74f, 8f).map { shift ->
            Color(
                android.graphics.Color.HSVToColor(
                    floatArrayOf(
                        ((hsv[0] + shift) % 360f + 360f) % 360f,
                        (hsv[1] * 0.9f).coerceIn(0f, 1f),
                        // Lifted in dark mode: a dark blob on a black ground
                        // is not a blob, it is nothing.
                        if (palette.isDark) 1f else 0.98f,
                    )
                )
            )
        }
    }

    // On an OLED screen the point of the black theme is that black pixels are
    // off. A full-strength field would light the whole panel, so it keeps the
    // character and loses most of the light.
    val strength = when {
        oled -> 0.38f
        palette.isDark -> 1f
        else -> 0.85f
    }

    // Reduce Motion stops the drift but keeps the colour. The setting is
    // about movement, not about making the app plainer - freezing at a phase
    // that still looks composed is the honest reading of it.
    val phase = if (settings.lowPowerAnimations) {
        0.17f
    } else {
        // Stepped eight times a second rather than animated at the display's
        // refresh rate. Over a seventy-second cycle each step moves a blob by
        // well under a pixel, so nothing is lost - and this is the home
        // screen of a messaging app, sitting open and idle for minutes at a
        // time, with a real backdrop blur sampling it. Redrawing that blur
        // 120 times a second to move nothing is the kind of thing that makes
        // a phone warm in your hand for no reason anybody could point at.
        val stepped by produceState(0f) {
            val start = android.os.SystemClock.elapsedRealtime()
            while (true) {
                val elapsed = (android.os.SystemClock.elapsedRealtime() - start).toFloat()
                value = (elapsed % CYCLE_MS) / CYCLE_MS
                delay(STEP_MS)
            }
        }
        stepped
    }

    Box(modifier.background(palette.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Larger than the screen on purpose - a blob whose edge enters
            // frame stops being weather and starts being a circle.
            val radius = maxOf(w, h) * 0.85f

            BLOBS.forEachIndexed { index, blob ->
                val t = 2f * PI.toFloat() * phase
                val cx = w * (blob.x + blob.driftX * sin(t * blob.speedX + blob.offset))
                val cy = h * (blob.y + blob.driftY * sin(t * blob.speedY + blob.offset * 1.7f))
                val color = colors[index % colors.size]
                    .copy(alpha = blob.alpha * strength * (if (palette.isDark) 1.35f else 1f))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/**
 * A blob's resting place and how far it wanders from it.
 *
 * The speeds are deliberately not whole multiples of each other: orbits that
 * share a period line back up, and a field that returns to the same
 * arrangement every cycle is one the eye learns and then starts predicting.
 */
private class Blob(
    val x: Float,
    val y: Float,
    val driftX: Float,
    val driftY: Float,
    val speedX: Float,
    val speedY: Float,
    val offset: Float,
    val alpha: Float,
)

private val BLOBS = listOf(
    Blob(x = 0.18f, y = 0.12f, driftX = 0.16f, driftY = 0.10f, speedX = 1f, speedY = 0.7f, offset = 0f, alpha = 0.15f),
    Blob(x = 0.86f, y = 0.26f, driftX = 0.13f, driftY = 0.14f, speedX = 0.61f, speedY = 1.13f, offset = 1.9f, alpha = 0.13f),
    Blob(x = 0.24f, y = 0.82f, driftX = 0.18f, driftY = 0.11f, speedX = 0.83f, speedY = 0.47f, offset = 3.4f, alpha = 0.12f),
    Blob(x = 0.78f, y = 0.94f, driftX = 0.12f, driftY = 0.13f, speedX = 1.29f, speedY = 0.91f, offset = 5.1f, alpha = 0.11f),
)
