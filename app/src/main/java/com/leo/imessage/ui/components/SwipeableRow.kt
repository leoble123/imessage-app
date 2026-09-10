package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class SwipeAction(
    val label: String,
    val color: Color,
    /** A single glyph drawn above the label, the way iOS does it. */
    val glyph: String = "",
    val onClick: () -> Unit,
)

/**
 * iOS-style swipe actions.
 *
 * Two behaviours make this feel native rather than like a generic dismissible:
 *  - the row rubber-bands past the action rail instead of stopping dead,
 *  - dragging far enough past the rail triggers the leading action outright
 *    on release (with a haptic at the threshold), matching Mail/Messages.
 */
@Composable
fun SwipeableRow(
    trailingActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    /** Revealed by swiping the other way - Pin, in Messages' case. */
    leadingActions: List<SwipeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()

    val actionWidth = 78.dp
    val railPx = with(density) { (actionWidth * trailingActions.size).toPx() }
    val leadingRailPx = with(density) { (actionWidth * leadingActions.size).toPx() }
    val fullSwipePx = railPx * 2.1f
    val leadingFullSwipePx = leadingRailPx * 2.1f

    val offset = remember { Animatable(0f) }
    // Plain holder rather than state: this only debounces the haptic, and
    // shouldn't trigger recomposition when it flips.
    val passedThreshold = remember { booleanArrayOf(false) }

    Box(modifier = modifier) {
        // matchParentSize, never fillMaxHeight: fillMaxHeight resolves against
        // the incoming constraint, and inside anything loosely bounded - a
        // preview card, a menu - that constraint is the whole screen. That's
        // how the action rails turned into full-height rainbow columns.
        // matchParentSize measures against the row itself, which is the only
        // height that was ever meant.
        Row(
            modifier = Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start),
        ) {
            leadingActions.forEach { action ->
                SwipeActionCell(action, actionWidth)
            }
        }

        Row(
            modifier = Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.End),
        ) {
            trailingActions.forEach { action ->
                SwipeActionCell(action, actionWidth)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(IntOffset(offset.value.roundToInt(), 0))
                    }
                }
                .pointerInput(trailingActions, leadingActions) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    -offset.value > fullSwipePx -> {
                                        trailingActions.firstOrNull()?.onClick?.invoke()
                                        0f
                                    }
                                    offset.value > leadingFullSwipePx -> {
                                        leadingActions.firstOrNull()?.onClick?.invoke()
                                        0f
                                    }
                                    -offset.value > railPx * 0.5f -> -railPx
                                    offset.value > leadingRailPx * 0.5f -> leadingRailPx
                                    else -> 0f
                                }
                                passedThreshold[0] = false
                                offset.animateTo(target, Motion.snappy())
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, Motion.snappy()) }
                        },
                    ) { _, dragAmount ->
                        scope.launch {
                            val raw = offset.value + dragAmount
                            // Rubber-band once dragged past either rail, and
                            // again past the full-swipe point, so the row
                            // never feels like it came loose.
                            val next = when {
                                raw < -railPx -> -railPx + (raw + railPx) * 0.45f
                                raw > leadingRailPx -> leadingRailPx + (raw - leadingRailPx) * 0.45f
                                else -> raw
                            }
                            val pastFull = -next > fullSwipePx ||
                                (leadingActions.isNotEmpty() && next > leadingFullSwipePx)
                            if (pastFull && !passedThreshold[0]) {
                                passedThreshold[0] = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!pastFull) {
                                passedThreshold[0] = false
                            }
                            offset.snapTo(next)
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * One action in a swipe rail.
 *
 * Glyph over label, centred, on a rounded card inset from the row edges -
 * iOS stopped using edge-to-edge colour slabs years ago, and the inset is
 * what stops a row of them reading as a stripe of raw paint.
 */
@Composable
private fun SwipeActionCell(action: SwipeAction, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 4.dp, horizontal = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(action.color)
            .pointerInput(action) {
                detectHorizontalDragGestures { _, _ -> }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (action.glyph.isNotEmpty()) {
                Text(
                    text = action.glyph,
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = action.label,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}
