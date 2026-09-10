package com.leo.imessage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.leo.imessage.data.Chat
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.data.Message
import com.leo.imessage.data.MessageRow
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/**
 * The reply chain for one message: the original on top, its replies beneath.
 *
 * Presented over the thread rather than as a separate screen, so the context
 * you tapped from stays visible behind it.
 */
@Composable
fun ReplyThreadSheet(
    root: Message,
    replies: List<Message>,
    chat: Chat,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = Motion.standard(),
        label = "threadAppear",
    )

    fun senderNameFor(m: Message): String? =
        if (m.isFromMe) null
        else chat.participants.firstOrNull { it.id == m.senderId }
            ?.displayName?.substringBefore(' ')

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f * appear))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    scaleX = 0.94f + 0.06f * appear
                    scaleY = 0.94f + 0.06f * appear
                    alpha = appear
                }
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surface)
                .padding(14.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${replies.size} ${if (replies.size == 1) "reply" else "replies"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryLabel,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            MessageBubble(
                row = MessageRow(
                    message = root,
                    groupPosition = GroupPosition.SINGLE,
                    showTimestampHeader = false,
                    showSenderName = false,
                    showDeliveryReceipt = false,
                ),
                senderName = senderNameFor(root),
                onLongPress = {},
            )

            replies.forEach { reply ->
                MessageBubble(
                    row = MessageRow(
                        message = reply,
                        groupPosition = GroupPosition.SINGLE,
                        showTimestampHeader = false,
                        showSenderName = !reply.isFromMe && chat.isGroup,
                        showDeliveryReceipt = false,
                    ),
                    senderName = senderNameFor(reply),
                    onLongPress = {},
                )
            }
        }
    }
}
