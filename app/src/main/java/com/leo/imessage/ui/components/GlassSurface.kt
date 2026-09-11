package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze

/**
 * Shared blur state. The scrolling content registers itself as the blur
 * source; bars register as children that sample it.
 */
val LocalHazeState = staticCompositionLocalOf { HazeState() }

/** Marks the content that glass surfaces blur. Put this on the scroll area. */
fun Modifier.glassSource(state: HazeState): Modifier = this.haze(state)

/**
 * The translucent "liquid glass" material for bars that content scrolls
 * underneath.
 *
 * This is a genuine backdrop blur: the scrolling content behind is sampled
 * and blurred live, then tinted. Content moving under the bar visibly smears
 * through it, which is the part that actually reads as glass - a flat
 * translucent fill never does, no matter how well tuned.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    tintAlpha: Float = 0.55f,
    blurRadius: Int = 32,
    /** Overrides the theme when the surface sits over a custom background. */
    darkBase: Boolean? = null,
    sheen: Boolean = true,
    hairlineAtBottom: Boolean = false,
    hairlineAtTop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val state = hazeState ?: LocalHazeState.current
    val base = if (darkBase ?: palette.isDark) Color.Black else Color.White

    Box(
        modifier = modifier.hazeChild(
            state = state,
            style = HazeStyle(
                backgroundColor = base,
                tints = listOf(HazeTint(base.copy(alpha = tintAlpha))),
                blurRadius = blurRadius.dp,
                noiseFactor = 0.06f,
            ),
        )
    ) {
        if (sheen) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (darkBase ?: palette.isDark) 0.07f else 0.30f),
                                Color.White.copy(alpha = 0f),
                            )
                        )
                    )
            )
        }

        content()

        if (hairlineAtBottom) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.separator)
                    .align(Alignment.BottomCenter)
            )
        }
        if (hairlineAtTop) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.separator)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * A floating glass pill - used for the scroll-to-bottom button and other
 * elements that hover over content rather than pinning to an edge.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val state = hazeState ?: LocalHazeState.current
    val base = if (darkBase ?: palette.isDark) Color.Black else Color.White

    val dark = darkBase ?: palette.isDark

    Box(
        modifier = modifier.hazeChild(
            state = state,
            style = HazeStyle(
                backgroundColor = base,
                tints = listOf(HazeTint(base.copy(alpha = 0.42f))),
                blurRadius = 26.dp,
                noiseFactor = 0.05f,
            ),
        ),
    ) {
        // A floating pane needs its own edge. Pinned to a screen edge a bar
        // borrows one from the frame; hovering over content it has nothing,
        // and without a rim it reads as a smudge rather than as glass.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.10f else 0.24f),
                            Color.White.copy(alpha = 0f),
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun ProvideHaze(state: HazeState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHazeState provides state, content = content)
}

/**
 * A pane of glass over the colour field - a conversation row, a card, a
 * grouped section.
 *
 * Everything the other glass surfaces have except the blur pass, and that is
 * a deliberate call rather than a shortcut. A backdrop blur is how you render
 * detail seen through glass; behind these there is only the ambient
 * gradient, and a blurred smooth gradient is the same smooth gradient. The
 * blur would cost a render pass per row to produce an identical image.
 *
 * What actually makes glass read as glass is the rest of it, and all of it is
 * here: a tint that lets the colour behind come through, a rim that catches
 * light along the top edge and loses it toward the bottom, a sheen down the
 * upper third, and a soft shadow so the pane sits *above* the field rather
 * than being painted into it. Surfaces that content genuinely moves behind -
 * the floating bars - still take the real blur, because there the detail is
 * real.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    darkBase: Boolean? = null,
    /** How much of the field shows through. Lower is more glass, less card. */
    tintAlpha: Float = 0.34f,
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val dark = darkBase ?: palette.isDark
    // Not pure white in light mode: a white pane over a pale field is white
    // on white, and the pane disappears along with its edges.
    val base = if (dark) Color(0xFF15161A) else Color(0xFFF7F7FA)

    Box(
        modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.38f),
                spotColor = Color.Black.copy(alpha = 0.38f),
            )
            .clip(shape)
            .background(base.copy(alpha = tintAlpha))
            .border(
                BorderStroke(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.22f else 0.55f),
                            Color.White.copy(alpha = if (dark) 0.06f else 0.13f),
                            Color.White.copy(alpha = if (dark) 0.03f else 0.07f),
                        )
                    ),
                ),
                shape = shape,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.06f else 0.18f),
                            Color.Transparent,
                        )
                    )
                )
        )
        content()
    }
}
