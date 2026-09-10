package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Message
import com.leo.imessage.ui.theme.LocalPalette

/**
 * The quoted message shown above a reply.
 *
 * Rendered smaller and dimmed so the reply itself stays the focus, and
 * tappable to open the full thread - the same affordance as tapping a reply
 * chain in Messages.
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
        modifier
            .widthIn(max = 250.dp)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.incomingBubble.copy(alpha = 0.45f))
            .clickable { onOpenThread() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
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
