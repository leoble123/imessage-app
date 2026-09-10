package com.leo.imessage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Poll
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/**
 * A poll, in the transcript.
 *
 * Results are always visible rather than hidden until you vote. Hiding them
 * is the "correct" survey-design answer - it stops the leader anchoring
 * everyone else - but a group chat picking a restaurant is not a survey, and
 * making people vote blind to see whether the plan already has three yeses
 * just gets the poll ignored.
 */
@Composable
fun PollCard(
    poll: Poll,
    myId: String,
    onVote: (String) -> Unit,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val onSurface = if (outgoing) Color.White else palette.label
    val total = poll.totalVotes.coerceAtLeast(1)

    Column(
        modifier
            .widthIn(max = 290.dp)
            .padding(2.dp),
    ) {
        Text(
            text = poll.question,
            style = MaterialTheme.typography.titleSmall,
            color = onSurface,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        poll.options.forEach { option ->
            val mine = myId in option.voters
            val share = option.voters.size.toFloat() / total
            // Bars grow into place, so a vote landing is something you see
            // happen rather than a number that was suddenly different.
            val fill by animateFloatAsState(
                targetValue = if (poll.totalVotes == 0) 0f else share,
                animationSpec = Motion.standard(),
                label = "pollBar",
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(onSurface.copy(alpha = 0.13f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !poll.isClosed,
                    ) { onVote(option.id) },
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = fill; transformOrigin =
                            androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .background(
                            if (mine) onSurface.copy(alpha = 0.34f)
                            else onSurface.copy(alpha = 0.18f)
                        )
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (mine) "✓  ${option.label}" else option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${option.voters.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurface.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Text(
            text = when {
                poll.isClosed -> "Closed · ${poll.totalVotes} votes"
                poll.totalVotes == 0 -> "No votes yet"
                poll.totalVotes == 1 -> "1 vote"
                else -> "${poll.totalVotes} votes"
            },
            style = MaterialTheme.typography.labelSmall,
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
