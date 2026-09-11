package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Materials
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
 * The material bars are made of: a live backdrop blur under a flat fill.
 *
 * Flat is the operative word and it took a wrong turn to learn it. This
 * previously carried a white rim graded down its edge and a sheen over its
 * top third, on the theory that light catching an edge is what makes glass
 * read as glass. On a photograph, yes. On a UI surface it reads as moulded
 * plastic, because a real one has none of it: UIKit's materials are a single
 * opacity over a blur and the only line anywhere on them is the hairline
 * where they meet content.
 *
 * The blur is the entire effect, and it has to be a real one. Content moving
 * under the bar visibly smears through it; a flat translucent fill never does
 * that, no matter how well tuned.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    /** Kept so callers can ask for the thicker material where it must stay readable. */
    thick: Boolean = false,
    /** Overrides the theme when the surface sits over a custom background. */
    darkBase: Boolean? = null,
    hairlineAtBottom: Boolean = false,
    hairlineAtTop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val state = hazeState ?: LocalHazeState.current
    val dark = darkBase ?: palette.isDark
    val fill = if (thick) Materials.thick(dark) else Materials.regular(dark)

    Box(
        modifier = modifier.hazeChild(
            state = state,
            style = HazeStyle(
                backgroundColor = if (dark) Color.Black else Color.White,
                tints = listOf(HazeTint(fill)),
                blurRadius = Materials.BLUR_RADIUS.dp,
                noiseFactor = 0f,
            ),
        )
    ) {
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
 * A floating capsule of the same material - the controls that hover over
 * content rather than pinning to an edge.
 *
 * One hairline of the separator colour rather than a lit rim. A floating
 * surface does need an edge, but the edge iOS gives it is a hairline, not a
 * highlight, and the difference between the two is the whole difference
 * between a control and a lozenge of frosted plastic.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    shape: Shape = CircleShape,
    /**
     * What fills it.
     *
     * Defaults to the bar material, which is right for a pill hovering over a
     * transcript. A pill hovering over the top of a list has nothing behind
     * it to be translucent *about*, and should be given a control fill
     * instead - see [Materials.control].
     */
    fill: Color? = null,
    /** Off by default: iOS puts no line around a control fill. */
    hairline: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val state = hazeState ?: LocalHazeState.current
    val dark = darkBase ?: palette.isDark

    Box(
        modifier = modifier
            .hazeChild(
                state = state,
                style = HazeStyle(
                    backgroundColor = if (dark) Color.Black else Color.White,
                    tints = listOf(HazeTint(fill ?: Materials.regular(dark))),
                    blurRadius = Materials.BLUR_RADIUS.dp,
                    noiseFactor = 0f,
                ),
            )
            .then(
                if (hairline) Modifier.border(0.5.dp, palette.separator, shape) else Modifier
            ),
    ) {
        content()
    }
}

@Composable
fun ProvideHaze(state: HazeState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHazeState provides state, content = content)
}
