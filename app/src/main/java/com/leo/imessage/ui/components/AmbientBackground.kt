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
import com.leo.imessage.ui.theme.Backdrop
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

    if (!settings.ambientBackground) {
        Box(modifier.background(palette.background))
        return
    }

    val colors = ambientColors(palette)

    // On an OLED screen the point of the black theme is that black pixels are
    // off. A full-strength field would light the whole panel, so it keeps the
    // character and loses most of the light.
    val strength = when {
        oled -> 0.38f
        palette.isDark -> 1f
        else -> 1f
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
            val span = maxOf(w, h)

            BLOBS.forEachIndexed { index, blob ->
                val t = 2f * PI.toFloat() * phase
                val cx = w * (blob.x + blob.driftX * sin(t * blob.speedX + blob.offset))
                val cy = h * (blob.y + blob.driftY * sin(t * blob.speedY + blob.offset * 1.7f))
                val color = colors[index % colors.size]
                    .copy(alpha = blob.alpha * strength * (if (palette.isDark) 1.35f else 1.15f))
                // Each one its own size. Four blobs of equal radius overlap
                // into a single even wash - which is a gradient, not a field.
                // It is the difference between the big ones that only ever
                // change the temperature of a corner and the tight ones that
                // actually read as light coming from somewhere.
                val radius = span * blob.radius

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
    /** As a fraction of the screen's longest side. */
    val radius: Float,
)

private val BLOBS = listOf(
    // Two wide ones setting the temperature of the whole screen...
    Blob(x = 0.16f, y = 0.06f, driftX = 0.18f, driftY = 0.12f, speedX = 1f, speedY = 0.7f, offset = 0f, alpha = 0.16f, radius = 0.95f),
    Blob(x = 0.84f, y = 0.96f, driftX = 0.14f, driftY = 0.13f, speedX = 0.61f, speedY = 1.13f, offset = 1.9f, alpha = 0.14f, radius = 0.88f),
    // ...and three tighter ones that read as light with a source.
    Blob(x = 0.92f, y = 0.20f, driftX = 0.15f, driftY = 0.16f, speedX = 0.83f, speedY = 0.47f, offset = 3.4f, alpha = 0.17f, radius = 0.52f),
    Blob(x = 0.12f, y = 0.68f, driftX = 0.20f, driftY = 0.14f, speedX = 1.29f, speedY = 0.91f, offset = 5.1f, alpha = 0.15f, radius = 0.46f),
    Blob(x = 0.58f, y = 0.42f, driftX = 0.24f, driftY = 0.22f, speedX = 0.47f, speedY = 1.37f, offset = 2.6f, alpha = 0.10f, radius = 0.38f),
)

/**
 * The colours the field is painted with.
 *
 * Hue-rotations of the accent rather than fixed colours, so the field belongs
 * to whatever accent is set instead of fighting it. The value is the part
 * that matters: a blob has to be *further from* the background than the
 * background is from itself, and on a white screen that means going darker.
 * Painting near-white blobs onto white is what made this whole effect
 * disappear in light mode while looking rich in dark - the code was the same,
 * the direction was backwards.
 */
@Composable
private fun ambientColors(palette: com.leo.imessage.ui.theme.AppPalette): List<Color> =
    remember(palette.accent, palette.isDark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(palette.accent.toArgb(), hsv)
        HUE_SHIFTS.map { shift ->
            Color(
                android.graphics.Color.HSVToColor(
                    floatArrayOf(
                        ((hsv[0] + shift) % 360f + 360f) % 360f,
                        if (palette.isDark) (hsv[1] * 0.9f).coerceIn(0f, 1f) else 0.55f,
                        if (palette.isDark) 1f else 0.80f,
                    )
                )
            )
        }
    }

/**
 * What the glass on this screen is actually sitting on.
 *
 * One colour for a field that is not one colour, which is an approximation
 * and is meant to be: the alternative is every pane sampling the pixels
 * beneath it, and a pane whose tint shifts as it slides over a gradient
 * flickers. The average is what the eye reports anyway.
 */
@Composable
fun rememberAmbientBackdrop(): Backdrop {
    val palette = LocalPalette.current
    val settings = LocalSettings.current
    if (!settings.ambientBackground) {
        return remember(palette.background) { Backdrop(palette.background) }
    }
    val colors = ambientColors(palette)
    val oled = settings.themeMode == ThemeMode.OLED
    return remember(colors, palette.background, oled) {
        val strength = if (oled) 0.38f else 1f
        val weight = if (palette.isDark) 1.35f else 1.15f
        var blended = palette.background
        BLOBS.forEachIndexed { index, blob ->
            val color = colors[index % colors.size]
            // Each blob contributes the share of the screen it actually
            // covers, so the wide faint ones count for more than the tight
            // bright ones - which is the opposite of averaging the list.
            val coverage = (blob.radius * blob.alpha * strength * weight).coerceIn(0f, 1f)
            blended = androidx.compose.ui.graphics.lerp(blended, color, coverage)
        }
        Backdrop(blended)
    }
}

private val HUE_SHIFTS = listOf(-52f, 22f, 86f, -14f, 48f)
