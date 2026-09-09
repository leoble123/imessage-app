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
import com.leo.imessage.ui.components.GroupAvatar
import com.leo.imessage.ui.components.MessageBubble
import com.leo.imessage.ui.components.TypingIndicator
import com.leo.imessage.ui.theme.LocalPalette
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
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    val rows = remember(messages) { buildRows(messages, chat.isGroup) }
    var menuFor by remember { mutableStateOf<MessageRow?>(null) }

    // Keep the newest message in view as the thread grows.
    LaunchedEffect(rows.size, chat.isTyping) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size)
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 104.dp, bottom = 90.dp, start = 12.dp, end = 12.dp),
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

                MessageBubble(
                    row = row,
                    senderName = senderName,
                    onLongPress = { menuFor = row },
                    modifier = Modifier.padding(
                        top = if (row.groupPosition == GroupPosition.SINGLE ||
                            row.groupPosition == GroupPosition.FIRST
                        ) 6.dp else 0.dp
                    ),
                )
            }

            if (chat.isTyping) {
                item(key = "typing") {
                    Box(Modifier.padding(top = 6.dp)) {
                        TypingIndicator()
                    }
                }
            }
        }

        // Nav bar with the contact's avatar, as in Messages.
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        MessageInputBar(
            onSend = onSend,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val focused = menuFor
        com.leo.imessage.ui.components.MessageContextMenu(
            visible = focused != null,
            onDismiss = { menuFor = null },
            onTapback = { kind ->
                focused?.let { onTapback(it.message.id, kind) }
                menuFor = null
            },
            actions = listOf(
                com.leo.imessage.ui.components.MenuAction("Reply") {},
                com.leo.imessage.ui.components.MenuAction("Copy") {},
                com.leo.imessage.ui.components.MenuAction("Delete", destructive = true) {},
            ),
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

@Composable
private fun MessageInputBar(
    onSend: (String, com.leo.imessage.data.MessageEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    var text by remember { mutableStateOf("") }
    var showEffects by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank()

    if (showEffects) {
        EffectPicker(
            onPick = { effect ->
                onSend(text.trim(), effect)
                text = ""
                showEffects = false
            },
            onDismiss = { showEffects = false },
        )
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        hairlineAtTop = true,
    ) {
        Row(
            Modifier
                .imePadding()
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.fieldBackground)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "iMessage",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.tertiaryLabel,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(8.dp))

            // The send button scales in only when there's something to send -
            // the same little pop iOS does. Long-pressing it opens the send
            // effects, as on iOS.
            AnimatedVisibility(
                visible = canSend,
                enter = scaleIn(com.leo.imessage.ui.theme.Motion.bouncy()) + fadeIn(),
                exit = scaleOut(com.leo.imessage.ui.theme.Motion.snappy()) + fadeOut(),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.outgoingBubble)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSend(text.trim(), com.leo.imessage.data.MessageEffect.NONE)
                                    text = ""
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showEffects = true
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Send-effect chooser, reached by long-pressing send. Each row previews what
 * the effect does to the bubble rather than just naming it.
 */
@Composable
private fun EffectPicker(
    onPick: (com.leo.imessage.data.MessageEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val options = listOf(
        com.leo.imessage.data.MessageEffect.SLAM to "Slam",
        com.leo.imessage.data.MessageEffect.LOUD to "Loud",
        com.leo.imessage.data.MessageEffect.GENTLE to "Gentle",
        com.leo.imessage.data.MessageEffect.INVISIBLE_INK to "Invisible Ink",
        com.leo.imessage.data.MessageEffect.NONE to "Send without effect",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surfaceElevated),
        ) {
            options.forEachIndexed { i, (effect, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (effect == com.leo.imessage.data.MessageEffect.NONE) {
                        palette.secondaryLabel
                    } else {
                        palette.accent
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(effect) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                )
                if (i != options.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(palette.separator)
                    )
                }
            }
        }
    }
}
