package com.leo.imessage.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * What is actually behind the glass, and everything the glass needs to know
 * about it.
 *
 * Every translucent surface in this app used to carry its own idea of the
 * answer: near-white in light mode, near-black in dark, at whatever alpha
 * that component's author liked. That works exactly once, on the background
 * the author happened to be looking at. On a white screen a near-white pane
 * at thirty percent *is* the white screen - measured off a real screenshot,
 * the cards and the pills were coming out between two and seven levels away
 * from the background they sat on, so the only thing defining them was their
 * shadow. In dark mode the same numbers looked rich. Two modes, two
 * unrelated results, from constants that never asked what was underneath.
 *
 * So nothing decides for itself any more. This is the single answer, derived
 * from the colour actually being painted, and every pane, rim, sheen, shadow
 * and scrim is a function of it. Change the background - theme, ambient
 * field, or something later that neither of those anticipated - and the
 * material follows on its own.
 */
@Immutable
class Backdrop(val color: Color) {

    val luminance: Float = color.luminance()

    /** Below this, white text belongs on it and light panes read as lit. */
    val isDark: Boolean = luminance < 0.42f

    /**
     * The body of a pane resting on this backdrop.
     *
     * Pushed away from the backdrop rather than toward a fixed grey: it keeps
     * the backdrop's hue, so a pane over a blue field is a blue-grey pane and
     * belongs to the screen, while still being far enough from it to have an
     * inside and an outside.
     *
     * Light needs a bigger push than dark. Screens emit, so lightening black
     * by a little is plainly visible while darkening white by the same amount
     * is nothing - which is precisely how a set of numbers tuned in dark mode
     * came out invisible in light.
     */
    private val paneTint: Color =
        if (isDark) lerp(color, Color.White, 0.20f) else lerp(color, Color.Black, 0.13f)

    fun pane(opacity: Float): Color = paneTint.copy(alpha = opacity(opacity))

    /**
     * How opaque a pane has to be *here* to read as a pane.
     *
     * Transparency only says something when there is something behind to see
     * through to. A pill floating over a plain white screen has nothing
     * behind it, so the same thirty percent that reads as glass over a
     * conversation reads as nothing at all - and the lighter the backdrop,
     * the more true that gets.
     */
    fun opacity(nominal: Float): Float =
        if (isDark) nominal else (nominal * 2f).coerceAtMost(0.86f)

    /** The lit top edge. Both modes have one; on light it is the brighter. */
    val rimTop: Color = Color.White.copy(alpha = if (isDark) 0.30f else 0.85f)

    val rimMid: Color = Color.White.copy(alpha = if (isDark) 0.08f else 0.22f)

    /**
     * The far edge. White on a dark backdrop, where the rim is light catching
     * a lip; dark on a light one, where it is the shadow under the same lip -
     * a white edge against white is not subtle, it is absent.
     */
    val rimBottom: Color =
        if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.10f)

    /** The highlight running down the top third of a pane. */
    val sheen: Color = Color.White.copy(alpha = if (isDark) 0.07f else 0.38f)

    /**
     * What a pane casts.
     *
     * Weaker on dark, and not because it should be subtle: a shadow is a
     * darkening, and there is very little left to darken on a black screen.
     * On light it is doing most of the work of lifting the pane off.
     */
    val shadow: Color = Color.Black.copy(alpha = if (isDark) 0.5f else 0.22f)

    /** The scrim behind a panel that has taken over the screen. */
    val scrim: Color = if (isDark) Color.Black else Color.White

    // Text, for anything drawing onto glass over this backdrop rather than
    // onto the theme's own surfaces.
    val label: Color = if (isDark) Color.White else Color.Black
    val secondaryLabel: Color = label.copy(alpha = 0.6f)
    val tertiaryLabel: Color = label.copy(alpha = 0.35f)
}

/**
 * Defaults to the theme's flat background, which is the truth on every screen
 * that does not paint something else behind its glass. A screen that does -
 * the conversation list and its colour field - provides its own.
 */
val LocalBackdrop = staticCompositionLocalOf { Backdrop(Color.Black) }
