package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Message
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The quoted original shown above a lone reply.
 *
 * Messages only does this when a message has exactly one reply - the reply
 * stays in the transcript and carries a shrunken, dimmed copy of what it
 * answers. The moment a second reply arrives both are hoisted into a thread
 * and the original grows a "2 Replies" link instead, which is what
 * [ReplyChainLink] handles.
 */
@Composable
fun ReplyQuote(
    parent: Message,
    senderName: String?,
    outgoing: Boolean,
    onOpenThread: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .padding(bottom = 3.dp, start = 8.dp, end = 8.dp)
            .graphicsLayer {
                // Smaller and faded, anchored to the side the reply hangs off
                // so the two read as one stack rather than two bubbles.
                scaleX = 0.9f
                scaleY = 0.9f
                alpha = 0.62f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (outgoing) 1f else 0f,
                    1f,
                )
            }
            .widthIn(max = 230.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(palette.incomingBubble.copy(alpha = 0.5f))
            .border(0.8.dp, palette.separator, RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onOpenThread() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = senderName ?: if (parent.isFromMe) "You" else "Them",
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryLabel,
        )
        Text(
            text = parent.text.ifBlank { "Attachment" },
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
