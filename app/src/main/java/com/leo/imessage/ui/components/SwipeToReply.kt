package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drag a bubble toward the middle of the screen to reply to it.
 *
 * The direction is mirrored for your own messages, and that isn't a detail:
 * a right-aligned bubble dragged rightward slides *off* the screen while the
 * arrow appears way over on the far left, so the gesture reads as happening
 * to some other message. Dragging each bubble away from its own edge means
 * the arrow always surfaces in the space the bubble just vacated, right next
 * to it, whichever side it started on.
 *
 * The arrow fades and scales in as you pull, the row rubber-bands past the
 * trigger point rather than sliding freely, and a haptic fires exactly at the
 * threshold - so you can feel the commit without watching the screen.
 */
@Composable
fun SwipeToReply(
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    /** Your own messages sit at the trailing edge and mirror the gesture. */
    outgoing: Boolean = false,
    /**
     * Off means the bubble is passed straight through with no gesture
     * wrapper at all, rather than a wrapper that quietly ignores drags -
     * a disabled detector still competes with the list's own scrolling.
     */
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val palette = LocalPalette.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    val scope = rememberCoroutineScope()

    val offset = remember { Animatable(0f) }
    val armed = remember { booleanArrayOf(false) }

    val triggerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    // +1 drags right (incoming, anchored left), -1 drags left (outgoing).
    val dir = if (outgoing) -1f else 1f

    Box(modifier) {
        // The drag offset is only ever read inside graphicsLayer blocks.
        // Reading it in the composable body instead would recompose this
        // node - and with it the entire bubble passed in as content - on
        // every frame of the drag, which is precisely what stops a gesture
        // from tracking the finger cleanly.
        Box(
            Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .size(30.dp)
                .graphicsLayer {
                    val p = (offset.value / triggerPx).coerceIn(0f, 1f)
                    scaleX = 0.5f + p * 0.5f
                    scaleY = 0.5f + p * 0.5f
                    alpha = p
                },
            contentAlignment = Alignment.Center,
        ) {
            ReplyArrow(
                color = palette.secondaryLabel,
                modifier = Modifier
                    .size(19.dp)
                    // The arrow points back the way the bubble came from.
                    .graphicsLayer { scaleX = dir },
            )
        }

        Box(
            Modifier
                .graphicsLayer { translationX = offset.value * dir }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value >= triggerPx) onReply()
                                armed[0] = false
                                offset.animateTo(0f, Motion.snappy())
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                armed[0] = false
                                offset.animateTo(0f, Motion.snappy())
                            }
                        },
                    ) { change, dragAmount ->
                        // Only rightward drags reply; past the trigger the pull
                        // gets heavy so it never feels like the row came loose.
                        val raw = offset.value + dragAmount * dir
                        val next = when {
                            raw <= 0f -> 0f
                            raw > triggerPx -> triggerPx + (raw - triggerPx) * 0.35f
                            else -> raw
                        }
                        if (next >= triggerPx && !armed[0]) {
                            armed[0] = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (next < triggerPx) {
                            armed[0] = false
                        }
                        if (abs(dragAmount) > 0f) change.consume()
                        scope.launch { offset.snapTo(next) }
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
private fun ReplyArrow(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.42f, h * 0.18f)
            lineTo(w * 0.10f, h * 0.48f)
            lineTo(w * 0.42f, h * 0.78f)
        }
        drawPath(
            path,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.13f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        val tail = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.10f, h * 0.48f)
            lineTo(w * 0.62f, h * 0.48f)
            cubicTo(w * 0.95f, h * 0.48f, w * 0.95f, h * 0.86f, w * 0.72f, h * 0.90f)
        }
        drawPath(
            tail,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.13f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}
