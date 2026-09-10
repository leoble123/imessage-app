package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.theme.LocalPalette

/**
 * Pinned conversations, as circles above the list.
 *
 * Messages keeps up to nine of these and sizes them by how many there are -
 * one or two get big circles, a full set gets small ones - which is what
 * stops a single pin looking lost and nine looking cramped. The unread dot
 * rides the top-left of the circle rather than sitting in a separate column,
 * because there's no row here to hang it off.
 */
@Composable
fun PinnedChatsRow(
    pinned: List<Chat>,
    onOpen: (Chat) -> Unit,
    onLongPress: (Chat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinned.isEmpty()) return
    val shown = pinned.take(9)
    val size = when (shown.size) {
        1, 2 -> 96.dp
        3, 4 -> 78.dp
        5, 6 -> 66.dp
        else -> 58.dp
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        shown.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { chat ->
                    PinnedChat(
                        chat = chat,
                        avatarSize = size,
                        onOpen = { onOpen(chat) },
                        onLongPress = { onLongPress(chat) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row left-aligned with the one above.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PinnedChat(
    chat: Chat,
    avatarSize: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.9f)

    Column(
        modifier = modifier
            .scaleFrom(scale)
            .pointerInput(chat.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onOpen() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (chat.isGroup) {
                GroupAvatar(chat.participants, avatarSize)
            } else {
                Avatar(chat.participants.first(), avatarSize)
            }
            if (chat.unreadCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )
            }
            if (chat.isMuted) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(palette.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "⊘",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.tertiaryLabel,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = chat.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
