package com.leo.imessage.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Light spilling off a coloured surface onto what's behind it.
 *
 * Real objects that emit or strongly reflect colour bleed a little of it onto
 * their surroundings, and its absence is one of those things nobody names but
 * everybody feels - a saturated blue rectangle sitting on black with a hard
 * edge reads as a sticker, not as something in the scene.
 *
 * Two rules keep it from looking like a cheap neon effect:
 *
 * - **It must be dim.** Anything you can consciously see as a glow is already
 *   far too strong. This tops out around 20% alpha and falls off fast.
 * - **It must be the object's own colour**, never white and never a fixed
 *   accent, or the light appears to come from somewhere the object isn't.
 *
 * Drawn as a few concentric outlines rather than a real blur: a blur here
 * would mean an offscreen pass per bubble, and the whole point is that this
 * is cheap enough to put on everything.
 */
fun Modifier.glow(
    shape: Shape,
    color: Color,
    radius: Dp = 16.dp,
    alpha: Float = 0.20f,
    layers: Int = 4,
): Modifier = this.drawBehind {
    val steps = layers.coerceAtLeast(1)
    for (i in steps downTo 1) {
        val spread = radius.toPx() * (i.toFloat() / steps)
        // Falls off with the square of distance, the way light actually does -
        // linear steps look like a stack of rings.
        val fade = alpha * (1f - i.toFloat() / (steps + 1)).let { it * it }
        val outline = shape.createOutline(
            androidx.compose.ui.geometry.Size(
                size.width + spread * 2f,
                size.height + spread * 2f,
            ),
            layoutDirection,
            this,
        )
        translate(-spread, -spread) {
            drawOutline(outline = outline, color = color.copy(alpha = fade))
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translate(
    dx: Float,
    dy: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    drawContext.transform.translate(dx, dy)
    block()
    drawContext.transform.translate(-dx, -dy)
}

/**
 * A soft pool of light under a floating control.
 *
 * Used where a glow around the whole silhouette would be too much - a send
 * button, a FAB - and what's wanted is just enough to lift it off the page.
 */
fun Modifier.underglow(
    color: Color,
    radius: Dp = 22.dp,
    alpha: Float = 0.28f,
): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.62f),
            radius = size.minDimension / 2f + r,
        ),
        radius = size.minDimension / 2f + r,
        center = Offset(size.width / 2f, size.height * 0.62f),
    )
}
