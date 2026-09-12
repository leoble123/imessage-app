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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.DeliveryState
import com.leo.imessage.data.Service
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.emojiGlyph
import com.leo.imessage.ui.components.rememberThumbnail
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
    /** Starts a FaceTime call with someone. Null hides the button entirely. */
    onFaceTime: (() -> Unit)? = null,
    onSetPinned: (String, Boolean) -> Unit = { _, _ -> },
    onSetMuted: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteChat: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit = {},
    onSetArchived: (String, Boolean) -> Unit = { _, _ -> },
    /** Clears every unread badge at once, from the pull-down summary. */
    onMarkAllRead: () -> Unit = {},
    /** The tail of a conversation, for the long-press peek. */
    recentMessages: ((String) -> List<com.leo.imessage.data.Message>)? = null,
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Pulling the list past the top opens the inbox summary.
    var showInbox by remember { mutableStateOf(false) }
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullThresholdPx = with(density) { 92.dp.toPx() }
    val pullMaxPx = with(density) { 150.dp.toPx() }
    val pullConnection = remember(pullThresholdPx, pullMaxPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                // Dragging back up has to undo the pull before the list gets
                // to move, or the panel stays hanging open over a list that
                // has already scrolled away underneath it.
                if (available.y >= 0f || pull.value <= 0f) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                val used = minOf(-available.y, pull.value / PULL_RESISTANCE)
                scope.launch {
                    pull.snapTo((pull.value - used * PULL_RESISTANCE).coerceAtLeast(0f))
                }
                return androidx.compose.ui.geometry.Offset(0f, -used)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                // Only what the list could not use, and only from a finger:
                // a fling that runs off the end must not open a panel nobody
                // asked for.
                if (available.y <= 0f ||
                    source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.Drag
                ) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                scope.launch {
                    pull.snapTo((pull.value + available.y * PULL_RESISTANCE).coerceAtMost(pullMaxPx))
                }
                return androidx.compose.ui.geometry.Offset(0f, available.y)
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (pull.value >= pullThresholdPx) showInbox = true
                pull.animateTo(0f, Motion.gentle())
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    // Crossing the threshold is confirmed by feel, so the gesture can be
    // completed without watching the screen.
    val pullReady = pull.value >= pullThresholdPx
    androidx.compose.runtime.LaunchedEffect(pullReady) {
        if (pullReady) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

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

    // How far the heading has been scrolled away.
    //
    // `canScrollBackward` first, and it is not belt and braces: a list with
    // two conversations in it cannot scroll at all, and every other term here
    // measures a scroll that never happened. Without it a short list came up
    // permanently collapsed - heading gone, on the one screen that had least
    // else to look at.
    val collapseProgress by remember {
        androidx.compose.runtime.derivedStateOf {
            when {
                !listState.canScrollBackward -> 0f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / 140f).coerceIn(0f, 1f)
            }
        }
    }

    val statusBarTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val navBarBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(palette.background)) {
        // The list is the blur source; the floating controls sample it, so
        // rows visibly smear through them on their way past.
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullConnection)
                .glassSource(hazeState),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = pull.value },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    // Only enough for the floating controls. The heading is a
                    // row of the list now rather than part of a bar, so it
                    // scrolls under them the way a large title should.
                    top = statusBarTop + TOP_BAR_INSET,
                    bottom = navBarBottom + BOTTOM_BAR_INSET,
                ),
            ) {
                val pinned = visibleChats.filter { it.isPinned }
                val rest = visibleChats.filterNot { it.isPinned }

                item(key = "heading") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)) {
                        Text(
                            text = if (showArchived) "Archived" else "Messages",
                            style = MaterialTheme.typography.displaySmall,
                            color = palette.label,
                        )
                        InboxStatusLine(chats = chats, showArchived = showArchived)
                    }
                }

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
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showArchived = !showArchived }
                                    .padding(horizontal = 20.dp, vertical = 13.dp),
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
                            RowSeparator()
                        }
                    }
                }

                if (visibleChats.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (query.isBlank()) "No Conversations" else "No Results",
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
                        showSeparator = chat.id != rows.lastOrNull()?.id,
                    )
                }
            }
        }

        // What the pull is for, revealed by the pull itself.
        if (pull.value > 1f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarTop + TOP_BAR_INSET)
                    .graphicsLayer {
                        translationY = pull.value - 42.dp.toPx()
                        alpha = (pull.value / pullThresholdPx).coerceIn(0f, 1f)
                    },
            ) {
                com.leo.imessage.ui.components.GlassPill(
                    modifier = Modifier.clip(RoundedCornerShape(50)),
                    hazeState = hazeState,
                    fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "↓",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.accent,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (pullReady) 180f else 0f
                            },
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = if (pullReady) "Release to catch up" else "Pull to catch up",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.label,
                        )
                    }
                }
            }
        }

        // --- Floating controls -------------------------------------------
        //
        // Not bars. A bar pinned edge to edge over a dark background is a
        // black rectangle no matter what material it claims to be made of -
        // there is nothing beside it to tell it apart from the screen. Pills
        // with the background running past them on every side are the only
        // shape that reads as glass rather than as a lid.

        val allPinned = remember(chats, showArchived) {
            chats.filter { it.isPinned && it.isArchived == showArchived }
        }
        val docked = allPinned.isNotEmpty() && !editing

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 6.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.leo.imessage.ui.components.GlassPill(
                    modifier = Modifier.clip(RoundedCornerShape(50)),
                    hazeState = hazeState,
                    fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                ) {
                    Row(
                        Modifier.padding(start = 13.dp, end = 15.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = palette.accent,
                            modifier = Modifier
                                .size(21.dp)
                                .clickable { onOpenSettings() },
                        )
                        Spacer(Modifier.width(13.dp))
                        Text(
                            text = if (editing) "Done" else "Edit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.accent,
                            modifier = Modifier.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                editing = !editing
                            },
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // The compact title only exists once the real one has gone.
                if (collapseProgress > 0.01f && !docked) {
                    com.leo.imessage.ui.components.GlassPill(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .graphicsLayer { alpha = collapseProgress },
                        hazeState = hazeState,
                        fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                    ) {
                        Text(
                            text = if (showArchived) "Archived" else "Messages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing && selected.isNotEmpty()) {
                        com.leo.imessage.ui.components.GlassPill(
                            modifier = Modifier.clip(RoundedCornerShape(50)),
                            hazeState = hazeState,
                            fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                        ) {
                            Text(
                                text = "Delete (${selected.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.destructive,
                                modifier = Modifier
                                    .clickable {
                                        selected.toList().forEach(onDeleteChat)
                                        selected.clear()
                                        editing = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (editing) {
                        com.leo.imessage.ui.components.GlassPill(
                            modifier = Modifier.clip(RoundedCornerShape(50)),
                            hazeState = hazeState,
                            fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                        ) {
                            Text(
                                text = if (selected.size == visibleChats.size) "None" else "All",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.accent,
                                modifier = Modifier
                                    .clickable {
                                        if (selected.size == visibleChats.size) selected.clear()
                                        else {
                                            selected.clear()
                                            selected.addAll(visibleChats.map { it.id })
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    } else {
                        com.leo.imessage.ui.components.GlassPill(
                            modifier = Modifier.clip(CircleShape),
                            hazeState = hazeState,
                            fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filter unread",
                                tint = if (unreadOnly) palette.accent else palette.secondaryLabel,
                                modifier = Modifier
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        unreadOnly = !unreadOnly
                                    }
                                    .padding(10.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                }
            }

            // The pins, once the list's own copy has scrolled away. Same
            // circles, caught on their way off the screen.
            if (docked && collapseProgress > 0.01f) {
                Box(
                    Modifier
                        .padding(start = 12.dp, top = 8.dp)
                        .graphicsLayer {
                            alpha = ((collapseProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                            translationY = -14.dp.toPx() * (1f - collapseProgress)
                        },
                ) {
                    com.leo.imessage.ui.components.GlassPill(
                        modifier = Modifier.clip(RoundedCornerShape(50)),
                        hazeState = hazeState,
                        fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                    ) {
                        com.leo.imessage.ui.components.PinnedDock(
                            pinned = allPinned,
                            onOpen = onOpenChat,
                            onLongPress = { actionsFor = it },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        // Search and compose float within thumb reach.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = navBarBottom + 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.leo.imessage.ui.components.GlassPill(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50)),
                hazeState = hazeState,
                fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
            ) {
                SearchField(query = query, onQueryChange = { query = it })
            }
            if (onFaceTime != null) {
                Spacer(Modifier.width(9.dp))
                com.leo.imessage.ui.components.GlassPill(
                    modifier = Modifier.clip(CircleShape),
                    hazeState = hazeState,
                    fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
                ) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = "New FaceTime",
                        tint = palette.accent,
                        modifier = Modifier
                            .clickable { onFaceTime() }
                            .padding(12.dp)
                            .size(21.dp),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            com.leo.imessage.ui.components.GlassPill(
                modifier = Modifier.clip(CircleShape),
                hazeState = hazeState,
                fill = com.leo.imessage.ui.theme.Materials.control(palette.isDark),
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "New message",
                    tint = palette.accent,
                    modifier = Modifier
                        .clickable { onCompose() }
                        .padding(13.dp)
                        .size(20.dp),
                )
            }
        }

        if (showInbox) {
            com.leo.imessage.ui.components.InboxSummarySheet(
                summary = remember(chats) { com.leo.imessage.util.buildInboxSummary(chats) },
                hazeState = hazeState,
                onOpenChat = onOpenChat,
                onMarkAllRead = onMarkAllRead,
                onDismiss = { showInbox = false },
            )
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
                    // The conversation, and enough of it to answer the
                    // question you long-pressed to ask. A menu that shows the
                    // row you were already looking at is a menu that made you
                    // open the thread anyway.
                    Box(Modifier.width(330.dp)) {
                        Column {
                            ChatRow(chat = chat, onOpen = {}, swipeEnabled = false)
                            val peek = remember(chat.id) {
                                recentMessages?.invoke(chat.id).orEmpty().takeLast(3)
                            }
                            if (peek.isNotEmpty()) {
                                RowSeparator()
                                Column(Modifier.padding(16.dp)) {
                                    peek.forEachIndexed { index, message ->
                                        if (index > 0) Spacer(Modifier.height(10.dp))
                                        PeekLine(chat = chat, message = message)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val palette = LocalPalette.current
    // No fill of its own: this sits inside a glass pill, and a solid field
    // painted inside glass is a solid field with a rim around it.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
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
    /** The hairline under the row. Off for the last one, and in previews. */
    showSeparator: Boolean = false,
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
    val tint = com.leo.imessage.ui.theme.chatTintFor(chat.avatarSeed)
    val ringColors = com.leo.imessage.ui.theme.chatRingColorsFor(chat.avatarSeed)

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
        Box {
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
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
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
                            .clip(CircleShape)
                            .background(tint)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // The same ring the pinned circles use, so an unread conversation
            // looks like itself whether it is pinned or in the list.
            Box(
                Modifier
                    .size(58.dp)
                    .drawBehind {
                        if (chat.unreadCount == 0) return@drawBehind
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                ringColors + ringColors.first(),
                                center = center,
                            ),
                            radius = (size.minDimension - stroke) / 2f - 1.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                            alpha = 0.9f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (chat.isGroup) {
                    GroupAvatar(chat.participants, 50.dp)
                } else {
                    Avatar(chat.participants.first(), 50.dp)
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold
                            else FontWeight.Normal,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.unreadCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tint)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = chat.lastMessage?.timestamp?.let { relativeTimeLabel(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chat.unreadCount > 0) tint else palette.tertiaryLabel,
                    )
                    Text(
                        text = " ›",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.tertiaryLabel,
                    )
                }
                Spacer(Modifier.height(3.dp))
                RowPreview(chat = chat, draft = draft, tint = tint)
            }
        }
        // Inset to start where the text does, which is what makes a list read
        // as rows of one thing rather than as stacked boxes.
        if (showSeparator) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 102.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.separator)
            )
        }
        }
    }
}

/** A full-width hairline, for rows that are not conversations. */
@Composable
private fun RowSeparator() {
    Box(
        Modifier
            .padding(start = 20.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalPalette.current.separator)
    )
}

/**
 * The second line of a row, showing what the message *is* rather than naming
 * its type.
 *
 * "📷 Photo" tells you a photo arrived, which you could have guessed. The
 * photo itself tells you whether it is the receipt you were waiting for or
 * another picture of a dog, and that is the entire question the list is there
 * to answer. Same reasoning for the rest: a reaction shows as the reaction, a
 * failed send shows as failed, and a draft shows as yours.
 */
@Composable
private fun RowPreview(
    chat: Chat,
    draft: String?,
    tint: Color,
) {
    val palette = LocalPalette.current
    val message = chat.lastMessage

    if (chat.isTyping) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "typing",
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
            )
            Spacer(Modifier.width(5.dp))
            com.leo.imessage.ui.components.TypingDots(color = tint)
        }
        return
    }

    if (!draft.isNullOrBlank()) {
        Text(
            text = "Draft: $draft",
            style = MaterialTheme.typography.bodyMedium,
            color = AppleColors.Orange,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    if (message == null) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.tertiaryLabel,
        )
        return
    }

    // Somebody reacting to your message is an event about *your* message, so
    // it reads as one instead of repeating the text as though they sent it.
    val reaction = message.tapbacks.lastOrNull { !it.fromMe }
    val failed = message.isFromMe && message.deliveryState == DeliveryState.FAILED
    val visual = message.attachments.firstOrNull { it.isVisual && it.uri != null }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (visual != null) {
            val thumb = com.leo.imessage.ui.components.rememberThumbnail(
                uri = visual.uri,
                kind = visual.kind,
                maxPx = 140,
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.label.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (visual.kind == com.leo.imessage.data.MediaKind.VIDEO) {
                    Text("▶", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.width(7.dp))
        }

        if (reaction != null) {
            Text(
                text = reaction.emoji ?: reaction.kind.emojiGlyph(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(5.dp))
        } else if (failed) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.destructive,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(palette.destructive.copy(alpha = 0.16f)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(6.dp))
        } else if (message.isFromMe) {
            // Your own last message, with how far it got. Nothing else in the
            // list can tell you a thread is waiting on the other person.
            Text(
                text = when (message.deliveryState) {
                    DeliveryState.SENDING -> "◌"
                    DeliveryState.READ -> "✓✓"
                    DeliveryState.DELIVERED -> "✓✓"
                    else -> "✓"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (message.deliveryState == DeliveryState.READ) tint
                    else palette.tertiaryLabel,
            )
            Spacer(Modifier.width(5.dp))
        }

        Text(
            text = when {
                reaction != null -> "Reacted to \"" + message.previewText().take(40) + "\""
                failed -> "Not delivered"
                else -> message.previewText(visual != null)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (chat.unreadCount > 0) palette.label else palette.secondaryLabel,
            maxLines = if (visual != null) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun com.leo.imessage.data.Message.previewText(hasThumbnail: Boolean = false): String = when {
    isUnsent -> "Message unsent"
    text.isNotBlank() -> text
    // Only name the attachment when there is no picture of it beside the
    // text; saying "Photo" next to the photo is just noise.
    hasThumbnail -> ""
    attachments.isEmpty() -> ""
    else -> attachments.first().let { a ->
        when (a.kind) {
            com.leo.imessage.data.MediaKind.IMAGE -> "Photo"
            com.leo.imessage.data.MediaKind.VIDEO -> "Video"
            com.leo.imessage.data.MediaKind.AUDIO -> "Audio message"
            else -> a.fileName
        }
    }
}

/** Selection is a plain toggle, kept out of the row so it stays testable. */
private fun toggleSelection(
    selected: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    id: String,
) {
    if (!selected.remove(id)) selected.add(id)
}

/** Room left at the top for the floating controls. */
private val TOP_BAR_INSET = 58.dp

/** And at the bottom, for search and compose. */
private val BOTTOM_BAR_INSET = 68.dp

/** Pulled distance per pixel dragged - the rubber band. */
private const val PULL_RESISTANCE = 0.5f

/**
 * The line under the title, saying what the list is currently doing.
 *
 * A heading that reads "Messages" over a list of messages is the one row on
 * this screen guaranteed to carry no information. This is the same real
 * estate answering the question you opened the app with.
 */
@Composable
private fun InboxStatusLine(chats: List<Chat>, showArchived: Boolean) {
    val palette = LocalPalette.current
    val status = remember(chats, showArchived) {
        val live = chats.filter { it.isArchived == showArchived }
        val typing = live.filter { it.isTyping }
        val unread = live.filter { it.unreadCount > 0 }
        val total = unread.sumOf { it.unreadCount }
        when {
            // Somebody typing right now outranks a backlog: it is the only
            // thing on this screen that expires.
            typing.size == 1 -> "${typing.first().displayName} is typing"
            typing.size > 1 -> "${typing.size} people are typing"
            total == 0 -> "All caught up"
            unread.size == 1 -> "$total unread from ${unread.first().displayName}"
            else -> "$total unread in ${unread.size} conversations"
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn(Motion.fade(200)) + slideInVertically { it / 3 }) togetherWith
                fadeOut(Motion.fade(140))
        },
        label = "inboxStatus",
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One message in the long-press peek.
 *
 * Named rather than aligned left and right like a transcript: three bubbles
 * in a 330dp card is a diagram of a conversation, not a conversation, and at
 * this size who said it is the only thing worth the width.
 */
@Composable
private fun PeekLine(chat: Chat, message: com.leo.imessage.data.Message) {
    val palette = LocalPalette.current
    val who = when {
        message.isFromMe -> "You"
        else -> chat.participants
            .firstOrNull { it.id == message.senderId }
            ?.displayName
            ?.substringBefore(' ')
            ?: chat.displayName.substringBefore(' ')
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = who,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (message.isFromMe) palette.secondaryLabel
                else com.leo.imessage.ui.theme.chatTintFor(chat.avatarSeed),
            modifier = Modifier.width(56.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message.previewText().ifBlank { "Attachment" },
            style = MaterialTheme.typography.bodySmall,
            color = palette.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
