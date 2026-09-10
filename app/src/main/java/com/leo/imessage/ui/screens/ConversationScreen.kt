package com.leo.imessage.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.data.Message
import com.leo.imessage.data.MessageRow
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.glassSource
import dev.chrisbanes.haze.HazeState
import com.leo.imessage.ui.components.GroupAvatar
import com.leo.imessage.ui.components.MessageBubble
import com.leo.imessage.ui.components.MessageInputBar
import com.leo.imessage.ui.components.TypingIndicator
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch
import com.leo.imessage.util.conversationTimestampHeader

/**
 * Turns a flat message list into display rows, deciding bubble grouping,
 * where timestamp dividers go, and which message carries the delivery
 * receipt. Kept out of the composable so it stays cheap and testable.
 */
fun buildRows(messages: List<Message>, isGroup: Boolean): List<MessageRow> {
    val groupWindowMs = 60_000L * 3
    val headerGapMs = 60_000L * 45

    val lastReceiptIndex = messages.indexOfLast { it.isFromMe }

    return messages.mapIndexed { i, msg ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)

        val samePrev = prev != null &&
            prev.isFromMe == msg.isFromMe &&
            prev.senderId == msg.senderId &&
            msg.timestamp - prev.timestamp < groupWindowMs &&
            !prev.isUnsent && !msg.isUnsent
        val sameNext = next != null &&
            next.isFromMe == msg.isFromMe &&
            next.senderId == msg.senderId &&
            next.timestamp - msg.timestamp < groupWindowMs &&
            !next.isUnsent && !msg.isUnsent

        val position = when {
            !samePrev && !sameNext -> GroupPosition.SINGLE
            !samePrev -> GroupPosition.FIRST
            !sameNext -> GroupPosition.LAST
            else -> GroupPosition.MIDDLE
        }

        MessageRow(
            message = msg,
            groupPosition = position,
            showTimestampHeader = prev == null || msg.timestamp - prev.timestamp > headerGapMs,
            showSenderName = isGroup && !msg.isFromMe && !samePrev,
            showDeliveryReceipt = i == lastReceiptIndex,
        )
    }
}

@Composable
fun ConversationScreen(
    chat: Chat,
    messages: List<Message>,
    onBack: () -> Unit,
    onSend: (String, com.leo.imessage.data.MessageEffect) -> Unit,
    onTapback: (String, com.leo.imessage.data.TapbackKind) -> Unit = { _, _ -> },
    onEmojiTapback: (String, String) -> Unit = { _, _ -> },
    onUnsend: (String) -> Unit = {},
    onOpenDetails: () -> Unit = {},
    backgroundId: String = "none",
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rows = remember(messages) { buildRows(messages, chat.isGroup) }
    var menuFor by remember { mutableStateOf<MessageRow?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    // Swipe the thread left to uncover per-message timestamps.
    val stampReveal = remember { androidx.compose.animation.core.Animatable(0f) }

    // Keep the newest message in view. The first pass jumps without
    // animating - animating into position on open is what made entering a
    // thread feel like it lurched.
    val firstLayout = remember { booleanArrayOf(true) }
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val target = rows.lastIndex
        if (firstLayout[0]) {
            firstLayout[0] = false
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }

    val background = com.leo.imessage.ui.theme.backgroundById(backgroundId)
    Box(
        Modifier
            .fillMaxSize()
            .then(
                background.brush?.let { Modifier.background(it) }
                    ?: Modifier.background(palette.background)
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .glassSource(hazeState)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { stampReveal.animateTo(0f, Motion.snappy()) }
                        },
                        onDragCancel = {
                            scope.launch { stampReveal.animateTo(0f, Motion.snappy()) }
                        },
                    ) { _, dragAmount ->
                        scope.launch {
                            // Only leftward drags reveal; rubber-band at the end.
                            val next = (stampReveal.value - dragAmount / 140f).coerceIn(0f, 1f)
                            stampReveal.snapTo(next)
                        }
                    }
                },
            contentPadding = PaddingValues(top = 104.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rows, key = { it.message.id }) { row ->
                if (row.showTimestampHeader) {
                    Text(
                        text = conversationTimestampHeader(row.message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.tertiaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                val senderName = chat.participants
                    .firstOrNull { it.id == row.message.senderId }
                    ?.displayName
                    ?.substringBefore(' ')

                com.leo.imessage.ui.components.SwipeToReply(
                    onReply = { replyingTo = row.message },
                    modifier = Modifier.padding(
                        top = if (row.groupPosition == GroupPosition.SINGLE ||
                            row.groupPosition == GroupPosition.FIRST
                        ) 6.dp else 0.dp
                    ),
                ) {
                    MessageBubble(
                        row = row,
                        senderName = senderName,
                        onLongPress = { menuFor = row },
                        timestampReveal = stampReveal.value,
                    )
                }
            }

            if (chat.isTyping) {
                item(key = "typing") {
                    Box(Modifier.padding(top = 6.dp)) {
                        TypingIndicator()
                    }
                }
            }
        }

            MessageInputBar(
                replyingTo = replyingTo,
                onCancelReply = { replyingTo = null },
                onSend = { text, effect ->
                    onSend(text, effect)
                    replyingTo = null
                },
                hazeState = hazeState,
            )
        }

        // Nav bar with the contact's avatar, as in Messages.
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            hazeState = hazeState,
            hairlineAtBottom = true,
        ) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clickable { onBack() }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = palette.accent,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onOpenDetails() },
                ) {
                    if (chat.isGroup) {
                        GroupAvatar(chat.participants, 34.dp)
                    } else {
                        Avatar(chat.participants.first(), 34.dp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = chat.displayName.substringBefore(' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.label,
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(38.dp))
            }
        }


        val focused = menuFor
        com.leo.imessage.ui.components.MessageContextMenu(
            visible = focused != null,
            onDismiss = { menuFor = null },
            onTapback = { kind ->
                focused?.let { onTapback(it.message.id, kind) }
                menuFor = null
            },
            onEmojiTapback = { emoji ->
                focused?.let { onEmojiTapback(it.message.id, emoji) }
                menuFor = null
            },
            actions = buildList {
                add(com.leo.imessage.ui.components.MenuAction("Reply") {})
                add(com.leo.imessage.ui.components.MenuAction("Copy") {})
                if (focused?.message?.isFromMe == true && focused.message.isUnsent.not()) {
                    add(com.leo.imessage.ui.components.MenuAction("Edit") {})
                    add(
                        com.leo.imessage.ui.components.MenuAction("Undo Send", destructive = true) {
                            onUnsend(focused.message.id)
                        }
                    )
                }
                add(com.leo.imessage.ui.components.MenuAction("Delete", destructive = true) {})
            },
            focusedContent = {
                if (focused != null) {
                    MessageBubble(
                        row = focused.copy(showDeliveryReceipt = false),
                        senderName = null,
                        onLongPress = {},
                    )
                }
            },
        )
    }
}


