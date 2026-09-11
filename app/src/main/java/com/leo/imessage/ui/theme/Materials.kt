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
     * The fill under a control - a search field, a round bar button.
     *
     * Not a material, and the distinction matters. A material is for a
     * surface content passes *behind*; at the top of a list there is nothing
     * behind it, so an eighty-percent white over white is white, and the
     * control disappears exactly where it most needs to be found. iOS gives
     * controls an opaque grey fill for that reason, and a search field on a
     * white screen is about fifteen levels down from it - which is quiet, and
     * is still a shape.
     */
    fun control(dark: Boolean): Color =
        if (dark) Color(0xF02C2C2E) else Color(0xF0EFEFF0)

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
