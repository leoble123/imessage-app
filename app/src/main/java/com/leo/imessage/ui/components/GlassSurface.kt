package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The translucent "liquid glass" material used for bars that content scrolls
 * underneath.
 *
 * Compose has no backdrop-blur primitive (graphicsLayer's RenderEffect blurs
 * the layer's own content, not what is behind it), so rather than fake a blur
 * badly this leans on the other half of what sells the effect: a translucent
 * tint, a top-down sheen, and a hairline edge. Content underneath stays
 * visible through it, which is the part the eye actually reads as glass.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tintAlpha: Float = 0.86f,
    sheen: Boolean = true,
    hairlineAtBottom: Boolean = false,
    hairlineAtTop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val base = if (palette.isDark) Color.Black else Color.White

    Box(modifier = modifier.background(base.copy(alpha = tintAlpha))) {
        if (sheen) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (palette.isDark) 0.06f else 0.28f),
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
