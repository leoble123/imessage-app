package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Materials
import com.leo.imessage.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * The single material every floating panel in the app is made of.
 *
 * Menus, sheets, trays and pickers were each rolling their own surface -
 * one opaque, one tinted, one with a border, one without - which is why the
 * app read as glass in some places and as a plain dialog in others.
 * Consistency here matters more than any individual panel looking good:
 * a material you can't predict doesn't read as a material at all.
 *
 * Real backdrop blur where a source is available, plus the same lit rim the
 * bubbles use, so a panel hovering over the conversation has an edge.
 */
@Composable
fun GlassSheet(
    shape: Shape,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    tintAlpha: Float = 0.55f,
    blurRadius: Int = 34,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val state = hazeState ?: LocalHazeState.current
    val dark = darkBase ?: palette.isDark

    Box(
        modifier
            // A cast shadow separates the panel from whatever it floats over
            // even when the two are nearly the same colour, which blur alone
            // cannot do.
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f),
            )
            .clip(shape)
            .hazeChild(
                state = state,
                style = HazeStyle(
                    backgroundColor = if (dark) Color.Black else Color.White,
                    tints = listOf(HazeTint(Materials.thick(dark))),
                    blurRadius = Materials.BLUR_RADIUS.dp,
                    noiseFactor = 0f,
                ),
            )
            // A hairline, not a lit rim. A panel needs an edge; iOS gives it
            // the separator colour and nothing else.
            .border(0.5.dp, palette.separator, shape),
    ) {
        content()
    }
}

/**
 * Drives a panel's appearance and, crucially, its *disappearance*.
 *
 * Overlays gated on a plain `if (visible)` pop out of existence the instant
 * the flag flips - you get a considered entrance and then the thing just
 * vanishes, which is what makes a UI feel like it's snapping rather than
 * flowing. This keeps the panel composed until the spring has actually
 * finished running backwards.
 *
 * Returns null while fully hidden, so callers can skip composing entirely.
 */
@Composable
fun rememberPanelProgress(visible: Boolean): State<Float>? {
    val progress = remember { Animatable(0f) }
    var present by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            present = true
            progress.animateTo(1f, Motion.standard())
        } else {
            progress.animateTo(0f, Motion.gentle())
            present = false
        }
    }

    return if (present || progress.value > 0.001f) progress.asState() else null
}

private fun Animatable<Float, *>.asState(): State<Float> = object : State<Float> {
    override val value: Float get() = this@asState.value
}

/**
 * The dimmed, blurred backdrop behind a panel.
 *
 * Blur rather than a flat scrim: dimming alone flattens whatever is behind
 * into a grey rectangle, while a blur keeps the conversation legible as
 * shapes, so the panel reads as sitting *in* the app rather than on top of
 * a screenshot of it.
 */
@Composable
fun GlassScrim(
    progress: () -> Float,
    hazeState: HazeState?,
    darkBase: Boolean?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val dark = darkBase ?: palette.isDark

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress() }
            .then(
                if (hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = if (dark) Color.Black else Color.White,
                            tints = listOf(HazeTint(Materials.scrim(dark))),
                            blurRadius = 22.dp,
                            noiseFactor = 0f,
                        ),
                    )
                } else {
                    Modifier.background(Materials.scrim(dark))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }
    )
}
