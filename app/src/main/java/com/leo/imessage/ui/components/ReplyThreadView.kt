package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.data.Message
import com.leo.imessage.data.MessageEffect
import com.leo.imessage.data.MessageRow
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * The inline reply thread.
 *
 * Messages doesn't push a new screen for this - the conversation stays
 * exactly where it was and goes soft behind, with only the thread in focus
 * and a composer whose placeholder changes to "Reply". That "still in the
 * room" quality is the whole feel of it, so this is a real sampled blur of
 * the live transcript (via Haze) rather than a dimming scrim: the messages
 * you were just looking at are still legible as shapes behind the thread.
 */
@Composable
fun ReplyThreadView(
    root: Message,
    replies: List<Message>,
    chat: Chat,
    hazeState: HazeState,
    darkBase: Boolean?,
    onDismiss: () -> Unit,
    onSendReply: (String, MessageEffect) -> Unit,
) {
    val palette = LocalPalette.current
    val dark = darkBase ?: palette.isDark
    val listState = rememberLazyListState()

    val appear = remember { Animatable(0f) }
    LaunchedEffect(root.id) { appear.animateTo(1f, Motion.standard()) }

    fun senderNameFor(m: Message): String? =
        if (m.isFromMe) null
        else chat.participants.firstOrNull { it.id == m.senderId }
            ?.displayName?.substringBefore(' ')

    // The thread's own transcript rides the keyboard too.
    ScrollWithKeyboard(listState)

    LaunchedEffect(replies.size) {
        if (replies.isNotEmpty()) listState.animateScrollToItem(replies.lastIndex)
    }

    Box(
        Modifier
            .fillMaxSize()
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = if (dark) Color.Black else Color.White,
                    tints = listOf(
                        HazeTint(
                            (if (dark) Color.Black else Color.White).copy(alpha = 0.62f)
                        )
                    ),
                    blurRadius = 26.dp,
                    noiseFactor = 0.04f,
                ),
            )
            .graphicsLayer { alpha = appear.value }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer {
                    // Rises a touch as it settles, so it reads as coming
                    // forward out of the transcript rather than fading on.
                    translationY = 14.dp.toPx() * (1f - appear.value)
                },
        ) {
            Text(
                text = if (replies.size == 1) "1 Reply" else "${replies.size} Replies",
                style = MaterialTheme.typography.labelLarge,
                color = palette.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 14.dp, bottom = 10.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "root") {
                    Column {
                        MessageBubble(
                            row = MessageRow(
                                message = root,
                                groupPosition = GroupPosition.SINGLE,
                                showTimestampHeader = false,
                                showSenderName = chat.isGroup && !root.isFromMe,
                                showDeliveryReceipt = false,
                            ),
                            senderName = senderNameFor(root),
                            sender = chat.participants.firstOrNull { it.id == root.senderId },
                            onLongPress = {},
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 40.dp)
                                .background(palette.separator)
                                .height(0.5.dp)
                        )
                    }
                }

                items(replies, key = { it.id }) { reply ->
                    MessageBubble(
                        row = MessageRow(
                            message = reply,
                            groupPosition = GroupPosition.SINGLE,
                            showTimestampHeader = false,
                            showSenderName = chat.isGroup && !reply.isFromMe,
                            showDeliveryReceipt = false,
                        ),
                        senderName = senderNameFor(reply),
                        sender = chat.participants.firstOrNull { it.id == reply.senderId },
                        onLongPress = {},
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            MessageInputBar(
                onSend = { text, effect -> onSendReply(text, effect) },
                hazeState = hazeState,
                darkBase = darkBase,
                placeholder = "Reply",
                autoFocus = true,
            )
        }
    }
}
