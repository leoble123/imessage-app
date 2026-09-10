package com.leo.imessage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
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
import com.leo.imessage.ui.components.underglow
import com.leo.imessage.ui.theme.AppleColors
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.leo.imessage.util.relativeTimeLabel

@Composable
fun ChatListScreen(
    chats: List<Chat>,
    onOpenChat: (Chat) -> Unit,
    onOpenSettings: () -> Unit = {},
    onCompose: () -> Unit = {},
    onSetPinned: (String, Boolean) -> Unit = { _, _ -> },
    onSetMuted: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteChat: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit = {},
    onSetArchived: (String, Boolean) -> Unit = { _, _ -> },
    drafts: Map<String, String> = emptyMap(),
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Chat?>(null) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var topBarHeight by remember { androidx.compose.runtime.mutableStateOf(0.dp) }
    var bottomBarHeight by remember { androidx.compose.runtime.mutableStateOf(0.dp) }

    val archivedCount = chats.count { it.isArchived }
    val visibleChats = remember(chats, query, unreadOnly, showArchived) {
        chats
            .filter { it.isArchived == showArchived }
            .filter { !unreadOnly || it.unreadCount > 0 }
            .filter { chat ->
                query.isBlank() ||
                    chat.displayName.contains(query, ignoreCase = true) ||
                    chat.lastMessage?.text?.contains(query, ignoreCase = true) == true ||
                    chat.participants.any { it.handle.contains(query, ignoreCase = true) }
            }
    }

    // Leaving edit mode must not leave a stale selection behind to act on.
    androidx.compose.runtime.LaunchedEffect(editing) { if (!editing) selected.clear() }
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }

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
                top = topBarHeight + 6.dp,
                bottom = bottomBarHeight + 6.dp,
            ),
        ) {
            val pinned = visibleChats.filter { it.isPinned }
            val rest = visibleChats.filterNot { it.isPinned }

            // Edit mode flattens the pins back into rows. As circles they
            // have nowhere to put a checkbox, so half the list simply could
            // not be selected - which is why only the unpinned chats
            // responded.
            if (pinned.isNotEmpty() && !editing) {
                item(key = "pins") {
                    com.leo.imessage.ui.components.PinnedChatsRow(
                        pinned = pinned,
                        onOpen = onOpenChat,
                        onLongPress = { actionsFor = it },
                    )
                }
            }

            if (archivedCount > 0 || showArchived) {
                item(key = "archived-entry") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showArchived = !showArchived }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (showArchived) "← Back to Messages" else "Archived",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.accent,
                            modifier = Modifier.weight(1f),
                        )
                        if (!showArchived) {
                            Text(
                                text = "$archivedCount",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.tertiaryLabel,
                            )
                        }
                    }
                }
            }

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

            val rows = if (editing) visibleChats else rest
            items(
                items = rows,
                key = { it.id },
                contentType = { "chat" },
            ) { chat ->
                ChatRow(
                    chat = chat,
                    onOpen = onOpenChat,
                    editing = editing,
                    isSelected = chat.id in selected,
                    onToggleSelected = { toggleSelection(selected, chat.id) },
                    onSetPinned = onSetPinned,
                    onSetMuted = onSetMuted,
                    onDeleteChat = onDeleteChat,
                    onMarkUnread = onMarkUnread,
                    onLongPress = { actionsFor = chat },
                    draft = drafts[chat.id],
                )
            }
        }

        // Search and compose live at the bottom, within thumb reach.
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
            hazeState = hazeState,
            hairlineAtTop = true,
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SearchField(query = query, onQueryChange = { query = it })
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.fieldBackground)
                        .clickable { onCompose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "New message",
                        tint = palette.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // Nav bar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } },
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
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = palette.accent,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { onOpenSettings() },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (editing) "Done" else "Edit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.accent,
                        modifier = Modifier.clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            editing = !editing
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    if (editing) {
                        Text(
                            text = if (selected.size == visibleChats.size) "None" else "All",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.accent,
                            modifier = Modifier
                                .clickable {
                                    if (selected.size == visibleChats.size) selected.clear()
                                    else {
                                        selected.clear()
                                        selected.addAll(visibleChats.map { it.id })
                                    }
                                }
                                .padding(end = 16.dp),
                        )
                    }
                    if (editing && selected.isNotEmpty()) {
                        Text(
                            text = "Delete (${selected.size})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.destructive,
                            modifier = Modifier.clickable {
                                selected.toList().forEach(onDeleteChat)
                                selected.clear()
                                editing = false
                            },
                        )
                    } else {
                        // Unread filter, matching Messages' Filters control.
                        Text(
                            text = if (unreadOnly) "Unread" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filter unread",
                            tint = if (unreadOnly) palette.accent else palette.secondaryLabel,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    unreadOnly = !unreadOnly
                                },
                        )
                    }
                }
                Text(
                    text = if (showArchived) "Archived" else "Messages",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.label,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }

        actionsFor?.let { chat ->
            com.leo.imessage.ui.components.ChatActionsMenu(
                chat = chat,
                onDismiss = { actionsFor = null },
                actions = listOf(
                    com.leo.imessage.ui.components.ChatAction(
                        if (chat.isPinned) "Unpin" else "Pin",
                        if (chat.isPinned) "\u2716" else "\u2691",
                    ) { onSetPinned(chat.id, !chat.isPinned) },
                    com.leo.imessage.ui.components.ChatAction(
                        if (chat.isMuted) "Show Alerts" else "Hide Alerts",
                        if (chat.isMuted) "\uD83D\uDD14" else "\uD83D\uDD15",
                    ) { onSetMuted(chat.id, !chat.isMuted) },
                    com.leo.imessage.ui.components.ChatAction(
                        if (chat.unreadCount > 0) "Mark as Read" else "Mark as Unread",
                        "\u25CF",
                    ) {
                        if (chat.unreadCount > 0) onOpenChat(chat) else onMarkUnread(chat.id)
                    },
                    com.leo.imessage.ui.components.ChatAction(
                        if (chat.isArchived) "Unarchive" else "Archive",
                        "\u2913",
                    ) { onSetArchived(chat.id, !chat.isArchived) },
                    com.leo.imessage.ui.components.ChatAction(
                        "Delete",
                        "\u2715",
                        destructive = true,
                    ) { onDeleteChat(chat.id) },
                ),
                hazeState = hazeState,
                preview = {
                    Box(Modifier.width(320.dp)) {
                        ChatRow(chat = chat, onOpen = {}, swipeEnabled = false)
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
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
private fun ChatRow(
    chat: Chat,
    onOpen: (Chat) -> Unit,
    editing: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onSetPinned: (String, Boolean) -> Unit = { _, _ -> },
    onSetMuted: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteChat: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit = {},
    onLongPress: () -> Unit = {},
    draft: String? = null,
    /** Off inside a preview card, where rails have nothing to slide out of. */
    swipeEnabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val rowHaptics = com.leo.imessage.ui.components.rememberHaptics()

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
        leadingActions = if (!swipeEnabled) emptyList() else listOf(
            SwipeAction(
                label = if (chat.isPinned) "Unpin" else "Pin",
                color = AppleColors.Orange,
                glyph = "\u2691",
            ) { onSetPinned(chat.id, !chat.isPinned) },
        ),
        trailingActions = if (!swipeEnabled) emptyList() else listOf(
            SwipeAction(
                label = "Delete",
                color = AppleColors.Red,
                glyph = "\uD83D\uDDD1",
            ) { onDeleteChat(chat.id) },
            SwipeAction(
                label = "Unread",
                color = AppleColors.Blue,
                glyph = "\u25CF",
            ) { onMarkUnread(chat.id) },
            SwipeAction(
                label = if (chat.isMuted) "Alerts" else "Mute",
                color = AppleColors.Indigo,
                glyph = if (chat.isMuted) "\uD83D\uDD14" else "\uD83D\uDD15",
            ) { onSetMuted(chat.id, !chat.isMuted) },
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
                        onTap = { if (editing) onToggleSelected() else onOpen(chat) },
                        onLongPress = {
                            rowHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // The selection circle slides the row over rather than appearing
            // on top of it, so nothing shifts under your thumb mid-tap.
            androidx.compose.animation.AnimatedVisibility(visible = editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (isSelected) palette.accent else Color.Transparent
                            )
                            .border(
                                1.5.dp,
                                if (isSelected) palette.accent else palette.tertiaryLabel,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Text(
                                "\u2713",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
            Box(Modifier.size(14.dp).padding(top = 20.dp)) {
                if (chat.unreadCount > 0) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .underglow(palette.accent, radius = 7.dp, alpha = 0.5f)
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
                    text = when {
                        chat.isTyping -> "typing…"
                        !draft.isNullOrBlank() -> "Draft: $draft"
                        else -> chat.lastMessage?.previewText().orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!draft.isNullOrBlank() && !chat.isTyping) palette.destructive
                        else palette.secondaryLabel,
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

/** Selection is a plain toggle, kept out of the row so it stays testable. */
private fun toggleSelection(
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    id: String,
) {
    if (!selected.remove(id)) selected.add(id)
}
