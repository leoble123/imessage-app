package com.leo.imessage.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The translucent materials iOS actually ships, and nothing else.
 *
 * The version before this one derived every surface from whatever colour
 * happened to be behind it, with a white rim graded down its edge and a sheen
 * over its top third. All three of those are what "foggy plastic" means: real
 * UIKit material has no rim, no sheen and no gradient anywhere in it. It is a
 * flat fill at a fixed opacity over a blur, and the only line on it is the
 * hairline separator where it meets content.
 *
 * So these are just the numbers, taken from UIKit's own materials rather than
 * invented. Two tones, light and dark, fixed - which is also the whole answer
 * to being consistent across backgrounds, because a constant cannot disagree
 * with itself the way a derivation can.
 */
object Materials {

    /** `.systemMaterial` - bars, pills, anything content scrolls under. */
    fun regular(dark: Boolean): Color =
        if (dark) Color(0xD1252525) else Color(0xD1F9F9F9)

    /** `.systemThickMaterial` - sheets and menus, which must stay readable. */
    fun thick(dark: Boolean): Color =
        if (dark) Color(0xF01C1C1E) else Color(0xF0F9F9F9)

    /**
     * A control that floats over content - a search field, a round bar
     * button, the pinned dock.
     *
     * The two things it has to do pull against each other, and the first
     * attempt at this only did one of them. A floating control needs a
     * defined tint, because at the top of a list there is nothing behind it
     * and a plain material over a plain background is that background. But
     * take that far enough to be safe - the ninety-four percent this was -
     * and it stops being glass: nothing passes through it, so nothing smears,
     * and what you get is a solid slab sitting on the screen.
     *
     * So: a tint dark or light enough to be a shape on its own, at an opacity
     * that still lets a row travelling underneath show through it. Both, at
     * once, rather than either at full strength.
     */
    fun floating(dark: Boolean): Color =
        if (dark) Color(0xD41E1E22) else Color(0xD4E7E7EC)

    /** What a full-screen panel dims the app behind it with. */
    fun scrim(dark: Boolean): Color =
        if (dark) Color(0x99000000) else Color(0x66000000)

    /**
     * How far the backdrop is blurred.
     *
     * iOS is around thirty points and pairs it with a saturation boost, which
     * is what stops a blurred photo going grey. There is no saturation control
     * here, so the radius stays where colour survives it.
     */
    const val BLUR_RADIUS = 30
}
