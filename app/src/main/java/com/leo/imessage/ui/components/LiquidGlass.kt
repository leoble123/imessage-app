package com.leo.imessage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
        // Measured off the reference, scanning across a bubble edge: the
        // wallpaper dips darker for a few pixels just outside the boundary
        // before the bubble begins. That is a contact shadow, and it is doing
        // as much of the separating as the surface is - a pane this sheer
        // would otherwise have nothing but its own rim to sit on.
        this
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = backdrop,
                    tints = listOf(HazeTint(tint)),
                    blurRadius = 30.dp,
                    noiseFactor = 0f,
                ),
            )
    }

    // The rim. The same edge scan shows a bright line just inside the
    // boundary, peaking at close to twice the brightness of the body before
    // falling off - not the faint hairline this had, which is why the shape
    // dissolved into a busy wallpaper.
    //
    // This is one line that follows the border, which is the whole difference
    // between it and the four painted highlights that were here before. Those
    // sat at fixed places on the bubble regardless of its shape or what was
    // behind it; a rim is where the surface actually turns.
    return body.border(
        BorderStroke(
            width = 1.dp,
            brush = SolidColor(
                if (dark) Color.White.copy(alpha = 0.24f)
                else Color.Black.copy(alpha = 0.14f)
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
     * Incoming, over a wallpaper - and these are veil alphas, not surface
     * alphas. The bubble is not a coloured pane at 50% over the wallpaper;
     * it is the *blurred wallpaper itself* with a thin wash pulled across it.
     *
     * The numbers are measured rather than chosen. Sampling the reference
     * shot - iOS 26 Messages over a dark photo - and solving each interior
     * pixel as `wash over wallpaper` gives a white wash at 0.08-0.16 across
     * three separate spots on one bubble, so 0.13 with a little headroom for
     * legibility. Nothing in that range resembles the 0.52 charcoal this had,
     * which is why the bubbles kept sinking into the wallpaper: the wash was
     * pushing them *down* toward it instead of lifting them off it.
     *
     * The light case inverts rather than mirrors. On a pale wallpaper a white
     * wash is the wallpaper, so the wash goes black - and lighter than its
     * counterpart, because iOS's own incoming grey on a white ground (#E9E9EB
     * on #FFFFFF) works out to a black wash at about 0.09.
     */
    const val INCOMING_VEIL_ON_DARK = 0.13f
    const val INCOMING_VEIL_ON_LIGHT = 0.10f

    /**
     * The fallback when there is a wallpaper but no blur to sample it with.
     *
     * A veil alpha is meaningless without the blur underneath it - 13% white
     * painted flat on a wallpaper is a smear, not a bubble - so this path
     * gets an opaque surface instead. It should be rare: it means a bubble
     * was handed a wallpaper and no HazeState.
     */
    const val INCOMING_UNBLURRED = 0.88f
}

/** Rebuilds a bubble gradient at a given transmission, keeping its shape. */
fun glassFill(colors: List<Color>, alpha: Float): Brush =
    Brush.verticalGradient(colors.map { it.copy(alpha = it.alpha * alpha) })

/** Single-colour variant, for the incoming bubble and other flat fills. */
fun glassFill(color: Color, alpha: Float): Brush =
    SolidColor(color.copy(alpha = color.alpha * alpha))

/**
 * The incoming bubble's fill, for when it is painted rather than blurred.
 *
 * Deliberately *not* the same value as the tint below. The tint is a wash
 * that only means anything composited over a live blur of the wallpaper;
 * this is what gets drawn when there is no blur, so it has to be a surface
 * that stands on its own.
 */
fun incomingGlassFill(
    grey: Color,
    overBackground: Boolean,
    backgroundIsDark: Boolean,
): Brush = SolidColor(
    when {
        !overBackground -> grey.copy(alpha = grey.alpha * GlassAlpha.INCOMING)
        backgroundIsDark -> Color(0xFF2C2C2E).copy(alpha = GlassAlpha.INCOMING_UNBLURRED)
        else -> Color(0xFFE9E9EB).copy(alpha = GlassAlpha.INCOMING_UNBLURRED)
    }
)

/**
 * The wash the blurred wallpaper is seen through.
 *
 * White over a dark wallpaper, black over a light one - in both directions
 * the bubble is moving *away* from whatever is behind it, which is the only
 * thing that makes a sheer surface visible. The wallpaper's own colour and
 * movement survive underneath either way, which is the part that reads as
 * glass rather than as a panel.
 */
fun incomingGlassTint(
    grey: Color,
    overBackground: Boolean,
    backgroundIsDark: Boolean,
): Color = when {
    !overBackground -> grey.copy(alpha = grey.alpha * GlassAlpha.INCOMING)
    backgroundIsDark -> Color.White.copy(alpha = GlassAlpha.INCOMING_VEIL_ON_DARK)
    else -> Color.Black.copy(alpha = GlassAlpha.INCOMING_VEIL_ON_LIGHT)
}
