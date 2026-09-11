package com.leo.imessage.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The three-dot typing bubble. Each dot runs the same keyframe cycle offset in
 * time, so the pulse travels left to right the way it does on iOS.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current

    Box(
        modifier = modifier
            .clip(BubbleShape(outgoing = false, group = GroupPosition.SINGLE))
            .background(palette.incomingBubble)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        TypingDots(color = palette.secondaryLabel, dot = 8.dp, gap = 5.dp)
    }
}

/**
 * The dots on their own, for anywhere there is no bubble to put them in -
 * the conversation list, where "typing" as a word and then three live dots
 * says the same thing twice but only one of the two proves it is happening
 * right now.
 */
@Composable
fun TypingDots(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    dot: androidx.compose.ui.unit.Dp = 5.dp,
    gap: androidx.compose.ui.unit.Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        0f at (i * 160)
                        1f at (i * 160 + 260)
                        0f at (i * 160 + 620)
                        0f at 1200
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(dot)
                    .scale(0.82f + phase * 0.28f)
                    .alpha(0.45f + phase * 0.55f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
