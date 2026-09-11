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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val backdrop = backdropFor(darkBase)

    Box(
        modifier = modifier.hazeChild(
            state = state,
            style = HazeStyle(
                backgroundColor = backdrop.color,
                tints = listOf(HazeTint(backdrop.pane(tintAlpha))),
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
                            listOf(backdrop.sheen, Color.Transparent)
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
    /** Must match what the caller clipped to, or the bend is in the wrong place. */
    cornerRadius: Dp = 999.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = hazeState ?: LocalHazeState.current
    val backdrop = backdropFor(darkBase)

    Box(
        modifier = modifier
            // Outermost, so the layer it makes holds the blurred backdrop and
            // the rim together - the shader bends the pane, not just its fill.
            .refractiveGlass(cornerRadius = cornerRadius)
            .hazeChild(
                state = state,
                style = HazeStyle(
                    backgroundColor = backdrop.color,
                    tints = listOf(HazeTint(backdrop.pane(0.42f))),
                    blurRadius = 26.dp,
                    noiseFactor = 0.05f,
                ),
            )
            // A floating pane needs its own edge. Pinned to a screen edge a
            // bar borrows one from the frame; hovering over content it has
            // nothing, and without a rim it reads as a smudge rather than as
            // glass - which on a light backdrop is the whole difference
            // between a control and a faint smear of nearly-white.
            .border(
                BorderStroke(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(backdrop.rimTop, backdrop.rimMid, backdrop.rimBottom)
                    ),
                ),
                shape = CircleShape,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(backdrop.sheen, Color.Transparent)))
        )
        content()
    }
}

/**
 * The backdrop a surface should render against.
 *
 * `darkBase` survives as an override for the handful of places that sit on
 * something the rest of the app cannot see - a photo viewer, a call screen -
 * and know better than the ambient answer.
 */
@Composable
private fun backdropFor(darkBase: Boolean?): com.leo.imessage.ui.theme.Backdrop {
    val backdrop = com.leo.imessage.ui.theme.LocalBackdrop.current
    if (darkBase == null || darkBase == backdrop.isDark) return backdrop
    return remember(darkBase) {
        com.leo.imessage.ui.theme.Backdrop(if (darkBase) Color.Black else Color.White)
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
    cornerRadius: Dp = 22.dp,
    darkBase: Boolean? = null,
    /** How much of the field shows through. Lower is more glass, less card. */
    tintAlpha: Float = 0.34f,
    elevation: Dp = 8.dp,
    /**
     * What the pane is looking through to.
     *
     * Without one the pane is a tinted fill, which over a smooth field is
     * indistinguishable from a blurred one - and there is nothing for the
     * refraction to bend, so the edge does nothing. With one, the field is
     * genuinely sampled, and the rim has something to compress.
     */
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = backdropFor(darkBase)

    Box(
        modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = backdrop.shadow,
                spotColor = backdrop.shadow,
            )
            .refractiveGlass(cornerRadius = cornerRadius)
            .clip(shape)
            .then(
                if (hazeState == null) {
                    Modifier.background(backdrop.pane(tintAlpha))
                } else {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = backdrop.color,
                            tints = listOf(HazeTint(backdrop.pane(tintAlpha))),
                            blurRadius = 24.dp,
                            noiseFactor = 0.04f,
                        ),
                    )
                }
            )
            .border(
                BorderStroke(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(backdrop.rimTop, backdrop.rimMid, backdrop.rimBottom)
                    ),
                ),
                shape = shape,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(backdrop.sheen, Color.Transparent)))
        )
        content()
    }
}
