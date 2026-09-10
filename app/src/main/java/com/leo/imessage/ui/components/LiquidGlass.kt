package com.leo.imessage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/**
 * The glass material used for message bubbles.
 *
 * Glass is four things stacked, and only the first is the transparency:
 *
 *  1. **Transmission.** What's behind genuinely shows through. Note that a
 *     backdrop blur over a smooth background is mathematically identical to
 *     alpha compositing over it - blurring a gradient returns the same
 *     gradient - so this isn't standing in for a blur, it *is* one, at no
 *     cost, on every bubble on screen.
 *  2. **Specular sheen.** Light catches the top of a curved surface.
 *  3. **A lit rim, with refraction under it.** The edge of a glass slab
 *     gathers light along the top and bends it just inside the boundary.
 *     Biggest tell there is, and the cheapest.
 *  4. **Caustic bounce.** Light returning up through the body off the
 *     bottom inside edge.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Brush,
    /** Lighting inverts over dark material, the way it does on iOS. */
    dark: Boolean,
    /** Off restores an opaque, pre-glass bubble. */
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this.clip(shape).drawBehind { drawRect(fill) }

    return this
        .clip(shape)
        .drawBehind {
            drawRect(fill)

            // Sheen across the top third.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.17f else 0.30f),
                    0.34f to Color.White.copy(alpha = if (dark) 0.05f else 0.08f),
                    0.62f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                )
            )

            // Refraction just inside the rim: a thin band where the surface
            // curves away and compresses what's behind it. Drawn as an inset
            // stroke so it hugs the silhouette instead of the bounding box.
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.22f else 0.40f),
                        Color.White.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                ),
            )

            // Caustic bounce along the bottom inside edge.
            drawRect(
                Brush.verticalGradient(
                    0.74f to Color.Transparent,
                    1f to Color.White.copy(alpha = if (dark) 0.10f else 0.17f),
                    startY = 0f,
                    endY = size.height,
                )
            )

            // Diagonal highlight off the leading top corner - what keeps the
            // surface from reading as a flat gradient.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.11f else 0.20f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.height * 1.6f,
                )
            )
        }
        .border(
            BorderStroke(
                width = 0.9.dp,
                // On dark material the whole rim is lit. On light material a
                // white rim is invisible, so the edge goes bright at the top
                // and picks up a faint shadow underneath - which is what
                // separates a pale glass pane from a flat rectangle.
                brush = if (dark) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.06f),
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.09f),
                        )
                    )
                },
            ),
            shape = shape,
        )
}

/**
 * How much of the backdrop a bubble lets through.
 *
 * Adaptive, and it has to be: over the default background there is nothing
 * behind a bubble but flat paint, so transparency buys nothing and
 * legibility is all that matters - iOS itself draws opaque bubbles there.
 * Over a chosen background there's something worth seeing, so the material
 * opens right up.
 */
object GlassAlpha {
    const val OUTGOING = 0.90f

    /**
     * Split by how dark the backdrop is, because white text has to survive
     * it. Over a dark background a translucent blue only gets deeper, so it
     * can open right up; over a light one the same alpha washes the blue out
     * until the label on top stops being readable, so it stays more closed.
     */
    const val OUTGOING_ON_DARK_BACKGROUND = 0.62f
    const val OUTGOING_ON_LIGHT_BACKGROUND = 0.82f

    /**
     * Incoming bubbles over a background stop being grey paint entirely.
     *
     * Painting translucent grey over a colour just gives you *grey* - which
     * is exactly what a tinted background used to look like. Real frosted
     * glass doesn't add grey, it lifts and desaturates whatever is behind
     * it, so over a background the fill becomes a near-clear white pane and
     * the background's own colour is what you see.
     */
    const val INCOMING = 0.92f
    const val INCOMING_PANE_ON_LIGHT = 0.34f
    const val INCOMING_PANE_ON_DARK = 0.17f
}

/** Rebuilds a bubble gradient at a given transmission, keeping its shape. */
fun glassFill(colors: List<Color>, alpha: Float): Brush =
    Brush.verticalGradient(colors.map { it.copy(alpha = it.alpha * alpha) })

/** Single-colour variant, for the incoming bubble and other flat fills. */
fun glassFill(color: Color, alpha: Float): Brush =
    SolidColor(color.copy(alpha = color.alpha * alpha))

/**
 * The incoming bubble's fill.
 *
 * Over the app's own background it stays the familiar grey. Over a chosen
 * background it becomes a clear pane instead, so the background reads
 * through as itself rather than through a grey wash.
 */
fun incomingGlassFill(
    grey: Color,
    overBackground: Boolean,
    backgroundIsDark: Boolean,
): Brush = when {
    !overBackground -> glassFill(grey, GlassAlpha.INCOMING)
    backgroundIsDark -> SolidColor(Color.White.copy(alpha = GlassAlpha.INCOMING_PANE_ON_DARK))
    else -> SolidColor(Color.White.copy(alpha = GlassAlpha.INCOMING_PANE_ON_LIGHT))
}
