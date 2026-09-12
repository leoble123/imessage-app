package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * How hard a message is being said, read off its punctuation.
 *
 * iMessage already has send effects, but somebody has to remember to use
 * them, which means they are used about twice and then never again. The way
 * people actually mark emphasis is by typing it - "WHAT?!" is not the same
 * sentence as "what" and never has been - and that is free to read.
 */
private enum class Emphasis(val durationMs: Int) {
    NONE(0),

    /** "?!" - disbelief. Shakes its head. */
    RATTLED(620),

    /** "!!" or shouting - impact. Lands and settles. */
    STRUCK(520),
}

private fun emphasisOf(text: String): Emphasis {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 120) return Emphasis.NONE
    if (trimmed.contains("?!") || trimmed.contains("!?")) return Emphasis.RATTLED
    if (trimmed.endsWith("!!")) return Emphasis.STRUCK

    // Shouting, but only when there is enough of it to be deliberate: two
    // capitals and a full stop is an abbreviation, not a raised voice.
    val letters = trimmed.filter { it.isLetter() }
    if (letters.length >= 4 && letters.all { it.isUpperCase() } && trimmed.endsWith("!")) {
        return Emphasis.STRUCK
    }
    return Emphasis.NONE
}

/**
 * A one-shot flinch when an emphatic message arrives.
 *
 * One-shot is the whole design. A bubble that shakes whenever it is on
 * screen is a bubble you cannot read, and it would fire again every time the
 * message scrolled back into view - so this runs only for something that
 * arrived in the last few seconds, which is the only moment the emphasis is
 * news. Everything after that is history and sits still.
 *
 * The animated value is read inside [graphicsLayer], so the spring costs a
 * matrix update per frame and never recomposes the bubble it is moving.
 */
@Composable
fun Modifier.kineticEmphasis(text: String, timestamp: Long): Modifier {
    val settings = LocalSettings.current
    // Every remember below is unconditional. Hiding one behind a short-circuit
    // would make the composition's shape depend on the text, and a bubble
    // whose remembered slots move when it is edited loses them.
    val kind = remember(text) { emphasisOf(text) }
    val fresh = remember(timestamp) { System.currentTimeMillis() - timestamp < FRESH_MS }
    val progress = remember { Animatable(0f) }

    val active = kind != Emphasis.NONE && fresh && !settings.lowPowerAnimations

    LaunchedEffect(active, kind) {
        if (!active) return@LaunchedEffect
        progress.snapTo(0f)
        // Linear, deliberately: the decay is in the curve below, and a spring
        // on top of an oscillation just makes the oscillation uneven.
        progress.animateTo(1f, tween(kind.durationMs, easing = LinearEasing))
    }

    if (!active) return this

    return this.graphicsLayer {
        val p = progress.value
        if (p >= 1f) return@graphicsLayer
        // Squared, so it dies away rather than stopping dead.
        val decay = (1f - p) * (1f - p)
        when (kind) {
            Emphasis.RATTLED -> {
                val wave = sin(p * PI.toFloat() * 9f)
                translationX = wave * 6.dp.toPx() * decay
                rotationZ = wave * 1.4f * decay
            }
            Emphasis.STRUCK -> {
                // One swell rather than a wobble: hit, then settle.
                val swell = sin(p * PI.toFloat()) * decay
                val s = 1f + swell * 0.09f
                scaleX = s
                scaleY = s
                translationY = -swell * 3.dp.toPx()
            }
            Emphasis.NONE -> Unit
        }
    }
}

/** How recently a message has to have landed for its emphasis to still be news. */
private const val FRESH_MS = 8_000L
