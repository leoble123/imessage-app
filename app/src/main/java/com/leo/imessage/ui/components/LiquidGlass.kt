package com.leo.imessage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * The glass a message bubble is made of.
 *
 * This used to stack four painted highlights on every bubble - a sheen down
 * the top third, a lit rim with a refraction band under it, a caustic bounce
 * off the bottom edge, and a diagonal glare off the leading corner - on the
 * theory that naming the optical effects of real glass and drawing each one
 * would add up to glass. It does not. It adds up to a plastic moulding with
 * reflections printed on it, because all four are *painted*: they sit in the
 * same place whatever is behind the bubble, and nothing that ignores its
 * surroundings reads as transparent.
 *
 * What actually makes a bubble read as glass is one thing done properly:
 * genuinely sampling what is behind it. Blur the wallpaper, tint it, and
 * stop. The reference material this is matched against - iOS 26 Messages
 * over an animated background - has no sheen, no glare, and a rim you have
 * to go looking for. The colour moving behind the bubble is the whole
 * effect.
 *
 * So: a real backdrop blur where there is a wallpaper worth blurring, a flat
 * tint over it, and at most a hairline. Over the app's plain background there
 * is nothing behind a bubble but paint, so it stays opaque - which is also
 * what iOS does there.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Brush,
    /**
     * The wallpaper to look through, if there is one. Null means the bubble
     * sits on flat paint and there is nothing to transmit.
     */
    hazeState: HazeState? = null,
    /**
     * The flat tint the blur is seen through.
     *
     * Separate from [fill] because a blur is tinted by one colour, not a
     * gradient - and the bubbles that take the blur are the incoming ones,
     * which are a single colour anyway. Null falls back to the opaque path.
     */
    tint: Color? = null,
    /** The colour the blur is composited against - the wallpaper's own base. */
    backdrop: Color = Color.Black,
    /** Lighting inverts over dark material. */
    dark: Boolean,
): Modifier {
    val body = if (hazeState == null || tint == null) {
        this.clip(shape).drawBehind { drawRect(fill) }
    } else {
        this
            .clip(shape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = backdrop,
                    tints = listOf(HazeTint(tint)),
                    blurRadius = 28.dp,
                    noiseFactor = 0f,
                ),
            )
    }

    // One hairline, and a faint one. On dark material it is the light
    // gathering along the edge; on light material that same white line is
    // invisible, so the edge takes a shadow instead. Anything stronger than
    // this is the moulding again.
    return body.border(
        BorderStroke(
            width = 0.7.dp,
            brush = SolidColor(
                if (dark) Color.White.copy(alpha = 0.14f)
                else Color.Black.copy(alpha = 0.07f)
            ),
        ),
        shape = shape,
    )
}

/**
 * How much of the backdrop a bubble lets through.
 *
 * Over the app's own background there is nothing behind a bubble but flat
 * paint, so transparency buys nothing and legibility is all that matters -
 * iOS draws opaque bubbles there too. Over a wallpaper the material opens up,
 * but only as far as the text on it can survive.
 */
object GlassAlpha {

    /**
     * Outgoing bubbles stay essentially solid, wallpaper or not.
     *
     * This is the correction the reference images forced. A blue bubble at
     * sixty percent over a purple wallpaper is not a glass blue bubble, it is
     * a purple bubble - the tint loses to whatever is behind it, and the one
     * colour in the app that carries meaning stops being reliable. iOS keeps
     * its outgoing bubbles opaque over every background it ships, and so does
     * this.
     */
    const val OUTGOING = 0.97f

    /** Incoming, on the plain background: the familiar grey, opaque. */
    const val INCOMING = 0.92f

    /**
     * Incoming, over a wallpaper.
     *
     * A neutral tint, and specifically *not* a lightened pane. The previous
     * version turned the bubble into near-clear white over a dark wallpaper,
     * reasoning that frosted glass lifts what is behind it. Real frosted
     * glass does; a dark bubble on a dark background is what the reference
     * actually shows, because the point is a readable surface for white text
     * and not a demonstration of optics.
     */
    const val INCOMING_ON_DARK = 0.52f
    const val INCOMING_ON_LIGHT = 0.60f
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
 * Over the app's own background it stays the familiar grey. Over a wallpaper
 * it becomes a neutral tint that darkens or lightens with the wallpaper
 * rather than with the theme - charcoal under a dark one, white under a light
 * one - so the text on it always has the same surface to sit on.
 */
fun incomingGlassFill(
    grey: Color,
    overBackground: Boolean,
    backgroundIsDark: Boolean,
): Brush = SolidColor(incomingGlassTint(grey, overBackground, backgroundIsDark))

/** The same decision as a flat colour, for the blur to be tinted with. */
fun incomingGlassTint(
    grey: Color,
    overBackground: Boolean,
    backgroundIsDark: Boolean,
): Color = when {
    !overBackground -> grey.copy(alpha = grey.alpha * GlassAlpha.INCOMING)
    backgroundIsDark -> Color(0xFF1A1A1C).copy(alpha = GlassAlpha.INCOMING_ON_DARK)
    else -> Color.White.copy(alpha = GlassAlpha.INCOMING_ON_LIGHT)
}
