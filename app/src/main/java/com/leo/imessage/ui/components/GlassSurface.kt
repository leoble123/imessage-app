package com.leo.imessage.ui.components

import androidx.compose.foundation.background
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
