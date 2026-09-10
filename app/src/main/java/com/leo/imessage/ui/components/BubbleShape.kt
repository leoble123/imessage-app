package com.leo.imessage.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.GroupPosition

/**
 * The iMessage bubble, drawn as a single continuous path.
 *
 * The previous version composed the body and the tail as two separate shapes.
 * That leaves a visible notch where they meet: the body's bottom corner curves
 * away with an 18dp radius while the tail attaches at the baseline, so a
 * sliver of background shows through between them. Drawing the whole
 * silhouette in one pass - walking the outline and bending out into the tail
 * where it belongs - means there is no seam to show.
 */
class BubbleShape(
    private val outgoing: Boolean,
    private val group: GroupPosition,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(size.height / 2f)
        val tight = with(density) { 5.dp.toPx() }
        val tailW = with(density) { 7.dp.toPx() }

        val hasTail = group == GroupPosition.SINGLE || group == GroupPosition.LAST
        // Corner facing the sender's edge tucks in for runs, so a group of
        // bubbles reads as one block.
        val innerTop = if (group == GroupPosition.MIDDLE || group == GroupPosition.LAST) tight else r
        val innerBottom = if (group == GroupPosition.MIDDLE || group == GroupPosition.FIRST) tight else r

        val w = size.width
        val h = size.height
        val path = Path()

        // Body occupies the full width; the tail bulges beyond it.
        val left = if (outgoing) 0f else tailW
        val right = if (outgoing) w - tailW else w

        // k: cubic control-point factor that approximates a quarter circle.
        val k = 0.5523f

        if (outgoing) {
            val trR = innerTop
            val brR = if (hasTail) 0f else innerBottom

            path.moveTo(left + r, 0f)
            path.lineTo(right - trR, 0f)
            if (trR > 0f) path.cubicTo(right - trR + trR * k, 0f, right, trR - trR * k, right, trR)

            if (hasTail) {
                // Down the right edge, then bow out into the tail and hook
                // back in along the baseline.
                path.lineTo(right, h - tailW * 1.75f)
                path.cubicTo(
                    right, h - tailW * 0.55f,
                    right + tailW * 0.55f, h - tailW * 0.30f,
                    right + tailW, h,
                )
                path.cubicTo(
                    right + tailW * 0.30f, h,
                    right - tailW * 0.10f, h,
                    right - tailW * 1.30f, h,
                )
            } else {
                path.lineTo(right, h - brR)
                if (brR > 0f) path.cubicTo(right, h - brR + brR * k, right - brR + brR * k, h, right - brR, h)
            }

            path.lineTo(left + r, h)
            path.cubicTo(left + r - r * k, h, left, h - r + r * k, left, h - r)
            path.lineTo(left, r)
            path.cubicTo(left, r - r * k, left + r - r * k, 0f, left + r, 0f)
        } else {
            val tlR = innerTop
            val blR = if (hasTail) 0f else innerBottom

            path.moveTo(left + tlR, 0f)
            path.lineTo(right - r, 0f)
            path.cubicTo(right - r + r * k, 0f, right, r - r * k, right, r)
            path.lineTo(right, h - r)
            path.cubicTo(right, h - r + r * k, right - r + r * k, h, right - r, h)

            if (hasTail) {
                path.lineTo(left + tailW * 1.30f, h)
                path.cubicTo(
                    left + tailW * 0.10f, h,
                    left - tailW * 0.30f, h,
                    left - tailW, h,
                )
                path.cubicTo(
                    left - tailW * 0.55f, h - tailW * 0.30f,
                    left, h - tailW * 0.55f,
                    left, h - tailW * 1.75f,
                )
            } else {
                path.lineTo(left + blR, h)
                if (blR > 0f) path.cubicTo(left + blR - blR * k, h, left, h - blR + blR * k, left, h - blR)
            }

            path.lineTo(left, tlR)
            if (tlR > 0f) path.cubicTo(left, tlR - tlR * k, left + tlR - tlR * k, 0f, left + tlR, 0f)
        }

        path.close()
        return Outline.Generic(path)
    }
}
