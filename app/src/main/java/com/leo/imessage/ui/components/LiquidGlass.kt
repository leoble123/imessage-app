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
import androidx.compose.ui.unit.dp

/**
 * The glass material used for message bubbles.
 *
 * What actually makes something read as glass is not blur on its own - it's
 * four things stacked, and the app was only doing the first:
 *
 *  1. **Transmission.** The fill is translucent, so what's behind genuinely
 *     shows through. Worth noting: a real backdrop blur over a smooth
 *     gradient background is mathematically identical to plain alpha
 *     compositing over it - blurring a linear gradient returns the same
 *     gradient - so this is not an approximation of the blur here, it *is*
 *     the blur, for a fraction of the cost. (The bars still use a true
 *     sampled blur via Haze, because scrolling bubbles pass under them and
 *     there the smear is the whole point.)
 *  2. **Specular sheen.** Light catches the top of a curved surface. Without
 *     this a translucent fill just looks faded.
 *  3. **A lit rim.** The edge of a glass slab gathers light - bright along
 *     the top, falling off toward the bottom. This is the single biggest
 *     tell, and the cheapest.
 *  4. **Caustic bounce.** A faint return of light along the bottom inside
 *     edge, where it refracts back up through the body.
 *
 * All of it is drawn per frame with no offscreen passes, so it costs nothing
 * measurable and can go on every bubble on screen.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Brush,
    /** Lighting is inverted over dark material, the way it is on iOS. */
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
                    0f to Color.White.copy(alpha = if (dark) 0.15f else 0.26f),
                    0.34f to Color.White.copy(alpha = if (dark) 0.04f else 0.07f),
                    0.62f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                )
            )

            // Caustic bounce along the bottom inside edge.
            drawRect(
                Brush.verticalGradient(
                    0.74f to Color.Transparent,
                    1f to Color.White.copy(alpha = if (dark) 0.09f else 0.15f),
                    startY = 0f,
                    endY = size.height,
                )
            )

            // A soft diagonal highlight off the leading top corner, which is
            // what keeps the surface from looking like a flat gradient.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.10f else 0.18f),
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
                // white rim is invisible, so the edge instead goes bright at
                // the top and picks up a faint shadow underneath - which is
                // what actually separates a pale glass bubble from a flat
                // grey rectangle.
                brush = if (dark) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.11f),
                            Color.White.copy(alpha = 0.05f),
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.03f),
                            Color.Black.copy(alpha = 0.07f),
                        )
                    )
                },
            ),
            shape = shape,
        )
}

/**
 * Translucency levels for the bubble fills.
 *
 * Outgoing bubbles carry more colour than incoming ones do, so they can give
 * up more alpha before they stop reading as blue.
 */
object GlassAlpha {
    // Held high enough that white text on the outgoing bubble keeps its
    // contrast: transmission is what you notice on a photo background, but
    // legibility is what you notice every single day.
    const val OUTGOING = 0.88f
    const val INCOMING = 0.78f
    /** Over a photo or gradient background there's more behind to show. */
    const val OUTGOING_OVER_BACKGROUND = 0.78f
    const val INCOMING_OVER_BACKGROUND = 0.60f
}

/** Rebuilds a bubble gradient at a given transmission, keeping its shape. */
fun glassFill(colors: List<Color>, alpha: Float): Brush =
    Brush.verticalGradient(colors.map { it.copy(alpha = it.alpha * alpha) })

/** Single-colour variant, for the incoming bubble and other flat fills. */
fun glassFill(color: Color, alpha: Float): Brush =
    androidx.compose.ui.graphics.SolidColor(color.copy(alpha = color.alpha * alpha))
