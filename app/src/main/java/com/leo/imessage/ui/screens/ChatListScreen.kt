package com.leo.imessage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.Service
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.glassSource
import dev.chrisbanes.haze.HazeState
import com.leo.imessage.ui.components.GroupAvatar
import com.leo.imessage.ui.components.SwipeAction
import com.leo.imessage.ui.components.SwipeableRow
import com.leo.imessage.ui.theme.AppleColors
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.leo.imessage.util.relativeTimeLabel

@Composable
fun ChatListScreen(
    chats: List<Chat>,
    onOpenChat: (Chat) -> Unit,
    onOpenSettings: () -> Unit = {},
    onCompose: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    var query by remember { mutableStateOf("") }

    val visibleChats = remember(chats, query) {
        if (query.isBlank()) chats
        else chats.filter { chat ->
            chat.displayName.contains(query, ignoreCase = true) ||
                chat.lastMessage?.text?.contains(query, ignoreCase = true) == true ||
                chat.participants.any { it.handle.contains(query, ignoreCase = true) }
        }
    }

    // The large title collapses into the compact bar as content scrolls under
    // it, the way a UIKit large-title nav bar does.
    val collapseProgress by remember {
        androidx.compose.runtime.derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(hazeState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 108.dp,
                bottom = 24.dp,
            ),
        ) {
            item(key = "search") {
                SearchField(query = query, onQueryChange = { query = it })
            }

            val pinned = visibleChats.filter { it.isPinned }
            val rest = visibleChats.filterNot { it.isPinned }

            if (visibleChats.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No Results",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.tertiaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            items(pinned, key = { it.id }) { chat ->
                ChatRow(chat, onOpenChat)
            }
            if (pinned.isNotEmpty() && rest.isNotEmpty()) {
                item(key = "pinned-sep") {
                    Box(
                        Modifier
                            .padding(start = 82.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(palette.separator)
                    )
                }
            }
            items(rest, key = { it.id }) { chat ->
                ChatRow(chat, onOpenChat)
            }
        }

        // Nav bar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            hazeState = hazeState,
            tintAlpha = 0.38f + 0.30f * collapseProgress,
            hairlineAtBottom = collapseProgress > 0.6f,
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.accent,
                        modifier = Modifier.clickable { onOpenSettings() },
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "New message",
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp).clickable { onCompose() },
                    )
                }
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.label,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.fieldBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.tertiaryLabel,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.label),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.tertiaryLabel,
                modifier = Modifier
                    .clickable { onQueryChange("") }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, onOpen: (Chat) -> Unit) {
    val palette = LocalPalette.current

    // iOS highlights a row the instant you touch it and clears the moment you
    // lift - no ripple, no delay. That immediacy is most of why taps feel
    // direct rather than laggy.
    var pressed by remember { mutableStateOf(false) }
    val pressTint by androidx.compose.animation.animateColorAsState(
        targetValue = if (pressed) palette.fieldBackground else palette.background,
        animationSpec = if (pressed) Motion.fade(40) else Motion.fade(220),
        label = "rowPress",
    )

    SwipeableRow(
        trailingActions = listOf(
            SwipeAction("Delete", AppleColors.Red) {},
            SwipeAction(if (chat.isMuted) "Unhide\nAlerts" else "Hide\nAlerts", AppleColors.Indigo) {},
        ),
    ) {
        Row(
            Modifier
                .background(pressTint)
                .pointerInput(chat.id) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onOpen(chat) },
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(14.dp).padding(top = 20.dp)) {
                if (chat.unreadCount > 0) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            if (chat.isGroup) {
                GroupAvatar(chat.participants, 50.dp)
            } else {
                Avatar(chat.participants.first(), 50.dp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = chat.lastMessage?.timestamp?.let { relativeTimeLabel(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.tertiaryLabel,
                    )
                    Text(
                        text = " ›",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.tertiaryLabel,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = chat.lastMessage?.previewText().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun com.leo.imessage.data.Message.previewText(): String = when {
    isUnsent -> "Message unsent"
    attachments.isNotEmpty() && text.isBlank() -> "📷 Photo"
    else -> text
}
