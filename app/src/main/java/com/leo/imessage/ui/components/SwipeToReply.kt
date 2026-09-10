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
 * Drag a bubble to the right to reply to it.
 *
 * The reply arrow fades and scales in as you pull, the row rubber-bands past
 * the trigger point rather than sliding freely, and a haptic fires exactly at
 * the threshold - so you can feel the commit without watching the screen.
 */
@Composable
fun SwipeToReply(
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val offset = remember { Animatable(0f) }
    val armed = remember { booleanArrayOf(false) }

    Box(modifier) {
        val triggerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val progress = (offset.value / triggerPx).coerceIn(0f, 1f)

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(30.dp)
                .scale(0.5f + progress * 0.5f)
                .alpha(progress),
            contentAlignment = Alignment.Center,
        ) {
            ReplyArrow(color = palette.secondaryLabel, modifier = Modifier.size(19.dp))
        }

        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
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
                        val raw = offset.value + dragAmount
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
