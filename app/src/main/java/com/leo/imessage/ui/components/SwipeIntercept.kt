package com.leo.imessage.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A vertical flick that beats its children to the gesture.
 *
 * Swiping the composer itself to summon or dismiss the keyboard has to work
 * when the swipe starts *on the text field*, and a text field claims drags
 * for its own selection handles. Watching the Initial pass means this sees
 * the drag on the way down through the tree, before the field does, and it
 * only consumes once the finger has clearly committed to a vertical
 * distance - so a plain tap still lands on the field and focuses it as
 * normal, and a horizontal drag still belongs to whatever wants it.
 */
fun Modifier.verticalFlick(
    threshold: Dp = 22.dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
): Modifier = pointerInput(Unit) {
    val thresholdPx = threshold.toPx()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var travelY = 0f
        var travelX = 0f
        var fired = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == first.id } ?: break
            if (change.changedToUpIgnoreConsumed()) break

            val delta = change.positionChange()
            travelY += delta.y
            travelX += delta.x

            if (!fired && abs(travelY) > thresholdPx && abs(travelY) > abs(travelX) * 1.4f) {
                fired = true
                if (travelY < 0) onUp() else onDown()
                change.consume()
            } else if (fired) {
                change.consume()
            }
        }
    }
}
