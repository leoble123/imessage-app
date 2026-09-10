package com.leo.imessage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.TapbackKind

/**
 * The six tapbacks drawn as vectors rather than typed as text.
 *
 * Rendering "HA" or "!!" as a text glyph never looks right - iOS draws these
 * as shapes with specific proportions and weights, and the difference is
 * immediately obvious side by side. These are drawn to match.
 */
@Composable
fun TapbackIcon(
    kind: TapbackKind,
    color: Color,
    modifier: Modifier = Modifier,
    emoji: String? = null,
) {
    if (kind == TapbackKind.ANY_EMOJI && emoji != null) {
        androidx.compose.material3.Text(
            text = emoji,
            fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = modifier,
        )
        return
    }

    Canvas(modifier) {
        when (kind) {
            TapbackKind.HEART -> drawHeart(color)
            TapbackKind.THUMBS_UP -> drawThumb(color, up = true)
            TapbackKind.THUMBS_DOWN -> drawThumb(color, up = false)
            TapbackKind.HAHA -> drawHaha(color)
            TapbackKind.EXCLAIM -> drawBangs(color)
            TapbackKind.QUESTION -> drawQuestion(color)
            TapbackKind.ANY_EMOJI -> Unit
        }
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.5f, h * 0.86f)
        cubicTo(w * 0.16f, h * 0.62f, w * 0.06f, h * 0.40f, w * 0.20f, h * 0.24f)
        cubicTo(w * 0.33f, h * 0.10f, w * 0.46f, h * 0.20f, w * 0.5f, h * 0.31f)
        cubicTo(w * 0.54f, h * 0.20f, w * 0.67f, h * 0.10f, w * 0.80f, h * 0.24f)
        cubicTo(w * 0.94f, h * 0.40f, w * 0.84f, h * 0.62f, w * 0.5f, h * 0.86f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawThumb(color: Color, up: Boolean) {
    val w = size.width
    val h = size.height
    // Drawn pointing up, then flipped for the down variant.
    val flip = if (up) 1f else -1f
    val cy = h * 0.5f
    fun y(v: Float) = cy + (v - cy) * flip

    // Fist
    val fist = Path().apply {
        moveTo(w * 0.34f, y(h * 0.44f))
        lineTo(w * 0.80f, y(h * 0.44f))
        cubicTo(w * 0.88f, y(h * 0.44f), w * 0.88f, y(h * 0.86f), w * 0.80f, y(h * 0.86f))
        lineTo(w * 0.34f, y(h * 0.86f))
        close()
    }
    drawPath(fist, color)

    // Thumb
    val thumb = Path().apply {
        moveTo(w * 0.34f, y(h * 0.46f))
        cubicTo(w * 0.34f, y(h * 0.30f), w * 0.42f, y(h * 0.28f), w * 0.46f, y(h * 0.14f))
        cubicTo(w * 0.49f, y(h * 0.05f), w * 0.62f, y(h * 0.08f), w * 0.58f, y(h * 0.22f))
        lineTo(w * 0.54f, y(h * 0.40f))
        lineTo(w * 0.34f, y(h * 0.40f))
        close()
    }
    drawPath(thumb, color)

    // Cuff
    val cuff = Path().apply {
        moveTo(w * 0.14f, y(h * 0.46f))
        lineTo(w * 0.32f, y(h * 0.46f))
        lineTo(w * 0.32f, y(h * 0.88f))
        lineTo(w * 0.14f, y(h * 0.88f))
        close()
    }
    drawPath(cuff, color)
}

/** "Ha" — set as two strokes rather than glyphs so weight stays even. */
private fun DrawScope.drawHaha(color: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.11f
    val stroke = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)

    // H
    drawLine(color, Offset(w * 0.16f, h * 0.28f), Offset(w * 0.16f, h * 0.72f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(w * 0.40f, h * 0.28f), Offset(w * 0.40f, h * 0.72f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.40f, h * 0.50f), sw, androidx.compose.ui.graphics.StrokeCap.Round)

    // a
    val a = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(w * 0.56f, h * 0.44f),
                Size(w * 0.28f, h * 0.28f),
            )
        )
    }
    drawPath(a, color, style = stroke)
    drawLine(color, Offset(w * 0.84f, h * 0.44f), Offset(w * 0.84f, h * 0.72f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
}

/** "‼" — two tapered bars with dots. */
private fun DrawScope.drawBangs(color: Color) {
    val w = size.width
    val h = size.height
    listOf(w * 0.36f, w * 0.64f).forEach { x ->
        val bar = Path().apply {
            moveTo(x - w * 0.075f, h * 0.20f)
            lineTo(x + w * 0.075f, h * 0.20f)
            lineTo(x + w * 0.045f, h * 0.60f)
            lineTo(x - w * 0.045f, h * 0.60f)
            close()
        }
        drawPath(bar, color)
        drawCircle(color, radius = w * 0.075f, center = Offset(x, h * 0.76f))
    }
}

private fun DrawScope.drawQuestion(color: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.12f
    val hook = Path().apply {
        moveTo(w * 0.30f, h * 0.34f)
        cubicTo(w * 0.32f, h * 0.14f, w * 0.72f, h * 0.14f, w * 0.68f, h * 0.36f)
        cubicTo(w * 0.65f, h * 0.52f, w * 0.50f, h * 0.50f, w * 0.50f, h * 0.66f)
    }
    drawPath(
        hook,
        color,
        style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.82f))
}
