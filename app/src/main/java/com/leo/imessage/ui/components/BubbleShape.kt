package com.leo.imessage.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.GroupPosition

/**
 * The iMessage bubble, including its tail.
 *
 * Two details matter for this to read as the real thing rather than a rounded
 * rectangle with a triangle stuck on:
 *  - the tail is a curl, not a triangle: it sweeps out of the bubble's corner
 *    and hooks back in, so the silhouette is continuous.
 *  - only the last bubble in a same-sender run gets a tail; the others tuck
 *    their inner corner in tight (small radius) so a run reads as one block.
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
        val r = with(density) { 18.dp.toPx() }
        val tight = with(density) { 6.dp.toPx() }
        val hasTail = group == GroupPosition.SINGLE || group == GroupPosition.LAST

        // Corner radii, in order: topStart, topEnd, bottomEnd, bottomStart.
        // The "inner" side is the one facing the sender's edge of the screen.
        val topInner = if (group == GroupPosition.MIDDLE || group == GroupPosition.LAST) tight else r
        val bottomInner = if (group == GroupPosition.MIDDLE || group == GroupPosition.FIRST) tight else r

        val path = Path()

        if (!hasTail) {
            val rr = if (outgoing) {
                RoundRect(
                    Rect(Offset.Zero, size),
                    topLeft = CornerRadius(r),
                    topRight = CornerRadius(topInner),
                    bottomRight = CornerRadius(bottomInner),
                    bottomLeft = CornerRadius(r),
                )
            } else {
                RoundRect(
                    Rect(Offset.Zero, size),
                    topLeft = CornerRadius(topInner),
                    topRight = CornerRadius(r),
                    bottomRight = CornerRadius(r),
                    bottomLeft = CornerRadius(bottomInner),
                )
            }
            path.addRoundRect(rr)
            return Outline.Generic(path)
        }

        // Bubble body inset from the edge to leave room for the tail curl.
        val tailW = with(density) { 6.dp.toPx() }
        val bodyLeft = if (outgoing) 0f else tailW
        val bodyRight = if (outgoing) size.width - tailW else size.width
        val h = size.height

        val body = if (outgoing) {
            RoundRect(
                Rect(bodyLeft, 0f, bodyRight, h),
                topLeft = CornerRadius(r),
                topRight = CornerRadius(topInner),
                bottomRight = CornerRadius(r),
                bottomLeft = CornerRadius(r),
            )
        } else {
            RoundRect(
                Rect(bodyLeft, 0f, bodyRight, h),
                topLeft = CornerRadius(topInner),
                topRight = CornerRadius(r),
                bottomRight = CornerRadius(r),
                bottomLeft = CornerRadius(r),
            )
        }
        path.addRoundRect(body)

        // The curl. Starts partway up the bubble's side, bows outward past the
        // edge, then hooks back in toward the baseline - the same silhouette
        // as the tail on a speech bubble drawn in one stroke.
        val tail = Path()
        if (outgoing) {
            val x = bodyRight
            tail.moveTo(x - r * 0.9f, h)
            tail.cubicTo(
                x + tailW * 0.15f, h,
                x + tailW * 0.95f, h - tailW * 0.55f,
                x + tailW, h - tailW * 1.6f,
            )
            tail.cubicTo(
                x + tailW * 0.55f, h - tailW * 0.2f,
                x + tailW * 0.2f, h,
                x - r * 0.2f, h,
            )
        } else {
            val x = bodyLeft
            tail.moveTo(x + r * 0.9f, h)
            tail.cubicTo(
                x - tailW * 0.15f, h,
                x - tailW * 0.95f, h - tailW * 0.55f,
                x - tailW, h - tailW * 1.6f,
            )
            tail.cubicTo(
                x - tailW * 0.55f, h - tailW * 0.2f,
                x - tailW * 0.2f, h,
                x + r * 0.2f, h,
            )
        }
        tail.close()
        path.addPath(tail)

        return Outline.Generic(path)
    }
}
