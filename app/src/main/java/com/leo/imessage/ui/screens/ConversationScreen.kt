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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.ime
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
import com.leo.imessage.ui.components.ConversationNavBar
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

    // Nothing is ever hidden from the transcript. Messages itself hoists
    // replies out into their thread, but a message you just sent silently
    // vanishing from the conversation is worse than a little duplication -
    // so replies stay where they were sent, each carrying a dimmed copy of
    // what it answers, and the original additionally grows a link into the
    // thread once there's more than one.
    val replyCounts = messages.groupingBy { it.replyToId }.eachCount()

    val lastReceiptIndex = messages.indexOfLast { it.isFromMe }

    return messages.mapIndexed { i, msg ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)

        // A reply always stands alone: it has a quote stacked above it, and
        // a quote wedged into the middle of a run would break the run's
        // silhouette anyway.
        val isReply = msg.replyToId != null
        val hasThreadLink = (replyCounts[msg.id] ?: 0) >= 2

        val samePrev = prev != null &&
            prev.isFromMe == msg.isFromMe &&
            prev.senderId == msg.senderId &&
            msg.timestamp - prev.timestamp < groupWindowMs &&
            !prev.isUnsent && !msg.isUnsent &&
            !isReply &&
            (replyCounts[prev.id] ?: 0) < 2
        val sameNext = next != null &&
            next.isFromMe == msg.isFromMe &&
            next.senderId == msg.senderId &&
            next.timestamp - msg.timestamp < groupWindowMs &&
            !next.isUnsent && !msg.isUnsent &&
            next.replyToId == null &&
            !hasThreadLink

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
            showAvatar = isGroup && !msg.isFromMe && !sameNext,
            replyCount = if (hasThreadLink) replyCounts[msg.id] ?: 0 else 0,
        )
    }
}

@Composable
fun ConversationScreen(
    chat: Chat,
    messages: List<Message>,
    onBack: () -> Unit,
    onSend: (
        String,
        com.leo.imessage.data.MessageEffect,
        String?,
        List<com.leo.imessage.data.Attachment>,
    ) -> Unit,
    onTapback: (String, com.leo.imessage.data.TapbackKind) -> Unit = { _, _ -> },
    onEmojiTapback: (String, String) -> Unit = { _, _ -> },
    onUnsend: (String) -> Unit = {},
    onOpenDetails: () -> Unit = {},
    onEdit: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onMarkRead: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    draft: String = "",
    onDraftChange: (String) -> Unit = {},
    onFaceTime: () -> Unit = {},
    backgroundId: String = "none",
) {
    val palette = LocalPalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rows = remember(messages) { buildRows(messages, chat.isGroup) }
    val byId = remember(messages) { messages.associateBy { it.id } }

    // Replying opens the message's thread rather than arming a banner on the
    // main composer. That's what Messages does, and it's the only way the
    // reply you just sent is somewhere you can see it: replies live in the
    // thread, so composing anywhere else means watching your own message
    // vanish from the transcript the moment it lands.
    fun threadFor(m: Message): Message = m.replyToId?.let { byId[it] } ?: m

    var menuFor by remember { mutableStateOf<MessageRow?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    // The message whose reply chain is being viewed, if any.
    var threadRoot by remember { mutableStateOf<Message?>(null) }
    // The attachment currently open full screen, if any.
    var viewing by remember { mutableStateOf<com.leo.imessage.data.Attachment?>(null) }
    // The message whose details sheet is open, if any.
    var infoFor by remember { mutableStateOf<Message?>(null) }
    // Attachment tray state lives here, not inside the composer: rendered
    // from inside the bar it was clipped by the bar's own bounds, which is
    // why it came up underneath the text field.
    var showTray by remember { mutableStateOf(false) }
    var trayRecording by remember { mutableStateOf(false) }
    var showEffectPicker by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }

    val matches = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else rows.mapIndexedNotNull { index, row ->
            index.takeIf { row.message.text.contains(searchQuery, ignoreCase = true) }
        }
    }
    // Stepping starts at the newest match, because the thing you're looking
    // for in a long thread is far more often recent than ancient.
    LaunchedEffect(matches) { matchIndex = (matches.size - 1).coerceAtLeast(0) }
    LaunchedEffect(matchIndex, matches) {
        matches.getOrNull(matchIndex)?.let { listState.animateScrollToItem(it) }
    }

    // Hoisted out of the item body: a lambda allocated inside `items` is a
    // new object on every pass, which makes every bubble's parameters look
    // changed and defeats Compose's skipping entirely. These are stable, so
    // an unaffected bubble is left alone.
    val openAttachment: (com.leo.imessage.data.Attachment) -> Unit =
        remember { { attachment -> viewing = attachment } }
    val showMenuFor: (MessageRow) -> Unit = remember { { row -> menuFor = row } }
    // Swiping a bubble arms a reply on the composer. It used to throw you
    // into the full thread view, which is the wrong trade: replying is the
    // common case and reading a thread is the rare one, so the gesture
    // should do the common thing and leave the thread behind the link.
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var staged by remember { mutableStateOf<List<com.leo.imessage.data.Attachment>>(emptyList()) }
    var stagedEffect by remember { mutableStateOf(com.leo.imessage.data.MessageEffect.NONE) }
    // Remembered so the tray can take the keyboard's exact place.
    var keyboardHeight by remember { mutableStateOf(300.dp) }
    val composerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.activity.compose.BackHandler(
        enabled = threadRoot != null || menuFor != null || viewing != null ||
            infoFor != null || showTray || replyingTo != null || showEffectPicker || searching
    ) {
        when {
            viewing != null -> viewing = null
            infoFor != null -> infoFor = null
            showEffectPicker -> showEffectPicker = false
            searching -> {
                searching = false
                searchQuery = ""
            }
            showTray -> showTray = false
            replyingTo != null -> replyingTo = null
            menuFor != null -> menuFor = null
            else -> threadRoot = null
        }
    }

    // Opening a thread clears its unread badge.
    LaunchedEffect(chat.id) { onMarkRead() }

    // Opening the tray shifts the transcript up the same way the keyboard
    // does, so the last message stays where you left it.
    LaunchedEffect(showTray) {
        if (showTray && rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }

    // Track the keyboard's height while it's up, so the tray can occupy
    // exactly the same space and swapping between them doesn't shift the
    // conversation.
    val imeInsets = androidx.compose.foundation.layout.WindowInsets.ime
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { imeInsets.getBottom(density) }
            .collect { px ->
                val dp = with(density) { px.toDp() }
                if (dp > 180.dp) {
                    keyboardHeight = dp
                    // Tapping the field while the tray is up should hand the
                    // space back to the keyboard, not stack the two.
                    showTray = false
                }
            }
    }

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

    // Nothing may ever end up behind the keyboard: the transcript rides up
    // with it frame for frame instead of being clipped by it.
    com.leo.imessage.ui.components.ScrollWithKeyboard(listState)
    val dismissKeyboardOnDrag =
        com.leo.imessage.ui.components.rememberKeyboardDismissConnection()

    val background = com.leo.imessage.ui.theme.backgroundById(backgroundId)
    // Both shells float over the transcript now, so the list has to reserve
    // room for them itself rather than being squeezed between two bars.
    var navBarHeight by remember { mutableStateOf(104.dp) }
    var composerHeight by remember { mutableStateOf(56.dp) }

    Box(Modifier.fillMaxSize()) {
        // The wallpaper and the transcript are one blur source together. If
        // only the messages were sampled, the floating glass would find
        // nothing behind it in the empty half of the screen and fall back to
        // flat tint - a dark capsule sitting on a purple wallpaper, which is
        // exactly the "cheap bar" look the glass is meant to replace.
        Box(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
        ) {
            com.leo.imessage.ui.components.ChatWallpaper(background)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Layout-phase inset, deliberately: reading the IME
                    // height in composition instead would recompose this
                    // whole screen on every frame of the keyboard animation.
                    // The wallpaper behind is a sibling and keeps the full
                    // screen, so nothing about the background moves.
                    .imePadding()
                    .nestedScroll(dismissKeyboardOnDrag)
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
            contentPadding = PaddingValues(
                top = navBarHeight + 6.dp,
                bottom = composerHeight + 8.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = rows,
                key = { it.message.id },
                // Bubbles, photo bubbles and voice notes are structurally
                // different subtrees. Without a content type the list reuses
                // one as the other and Compose rebuilds it from scratch;
                // with it, scrolling recycles like for like.
                contentType = { row ->
                    when {
                        row.message.isUnsent -> "unsent"
                        row.message.attachments.any { it.isVisual } -> "media"
                        row.message.attachments.isNotEmpty() -> "attachment"
                        else -> "text"
                    }
                },
            ) { row ->
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
                    outgoing = row.message.isFromMe,
                    onReply = {
                        replyingTo = row.message
                        composerFocus.requestFocus()
                        keyboard?.show()
                    },
                    modifier = Modifier.padding(
                        top = if (row.groupPosition == GroupPosition.SINGLE ||
                            row.groupPosition == GroupPosition.FIRST
                        ) 6.dp else 0.dp
                    ),
                ) {
                    MessageBubble(
                        row = row,
                        onCustomBackground = background.brush != null,
                        backgroundIsDark = background.brush != null && background.isDark,
                        sender = chat.participants.firstOrNull { it.id == row.message.senderId },
                        senderName = senderName,
                        onLongPress = { showMenuFor(row) },
                        timestampReveal = { stampReveal.value },
                        replyCount = row.replyCount,
                        replyParent = row.message.replyToId?.let { byId[it] },
                        replyParentSender = row.message.replyToId
                            ?.let { byId[it] }
                            ?.let { parent ->
                                if (parent.isFromMe) "You"
                                else chat.participants
                                    .firstOrNull { it.id == parent.senderId }
                                    ?.displayName
                                    ?.substringBefore(' ')
                            },
                        onOpenThread = { threadRoot = threadFor(row.message) },
                        onOpenAttachment = openAttachment,
                        highlight = searchQuery.takeIf { searching && it.isNotBlank() },
                        isActiveMatch = searching &&
                            matches.getOrNull(matchIndex) == rows.indexOf(row),
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
        }

        MessageInputBar(
                onSend = { text, effect, attachments ->
                    onSend(text, effect, replyingTo?.id, attachments)
                    replyingTo = null
                    staged = emptyList()
                    stagedEffect = com.leo.imessage.data.MessageEffect.NONE
                    showTray = false
                },
                hazeState = hazeState,
                darkBase = if (background.brush != null) background.isDark else null,
                editing = editingMessage,
                focusRequester = composerFocus,
                onCancelEdit = { editingMessage = null },
                onCommitEdit = { newText ->
                    editingMessage?.let { onEdit(it.id, newText) }
                    editingMessage = null
                },
                replyingTo = replyingTo,
                onCancelReply = { replyingTo = null },
                draft = draft,
                draftKey = chat.id,
                onDraftChange = onDraftChange,
                trayOpen = showTray,
                onToggleTray = {
                    if (showTray) {
                        showTray = false
                        trayRecording = false
                    } else {
                        keyboard?.hide()
                        trayRecording = false
                        showTray = true
                    }
                },
                onRecordAudio = {
                    keyboard?.hide()
                    trayRecording = true
                    showTray = true
                },
                staged = staged,
                onRemoveStaged = { att -> staged = staged.filterNot { it.id == att.id } },
                stagedEffect = stagedEffect,
                onClearStagedEffect = {
                    stagedEffect = com.leo.imessage.data.MessageEffect.NONE
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .onSizeChanged {
                        composerHeight = with(density) { it.height.toDp() }
                    },
            )

            val trayProgress = com.leo.imessage.ui.components.rememberPanelProgress(showTray)
            if (trayProgress != null) {
                com.leo.imessage.ui.components.AttachmentTray(
                    onAttach = { added -> staged = staged + added },
                    onRequestEffects = { showEffectPicker = true },
                    onDismiss = {
                        showTray = false
                        trayRecording = false
                    },
                    onShareLocation = { onShareLocation() },
                    startRecording = trayRecording,
                    hazeState = hazeState,
                    darkBase = if (background.brush != null) background.isDark else null,
                    progress = { trayProgress.value },
                )
            }

        if (searching) {
            com.leo.imessage.ui.components.ConversationSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                matchCount = matches.size,
                currentMatch = matchIndex,
                onPrevious = {
                    if (matches.isNotEmpty()) {
                        matchIndex = (matchIndex - 1 + matches.size) % matches.size
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) matchIndex = (matchIndex + 1) % matches.size
                },
                onClose = {
                    searching = false
                    searchQuery = ""
                },
                hazeState = hazeState,
                darkBase = if (background.brush != null) background.isDark else null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
            )
        } else ConversationNavBar(
            chat = chat,
            hazeState = hazeState,
            darkBase = if (background.brush != null) background.isDark else null,
            onBack = onBack,
            onOpenDetails = onOpenDetails,
            onFaceTime = onFaceTime,
            onSearch = { searching = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
        )

        // Jump-to-latest, the way Messages shows one once you've scrolled up.
        val awayFromBottom by remember {
            androidx.compose.runtime.derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount - last > 3
            }
        }
        com.leo.imessage.ui.components.JumpToLatest(
            visible = awayFromBottom,
            hazeState = hazeState,
            darkBase = if (background.brush != null) background.isDark else null,
            onClick = {
                scope.launch {
                    if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = composerHeight + 12.dp),
        )

        if (showEffectPicker) {
            com.leo.imessage.ui.components.EffectPicker(
                onPick = { effect ->
                    showEffectPicker = false
                    stagedEffect = effect
                },
                onDismiss = { showEffectPicker = false },
                hazeState = hazeState,
                darkBase = if (background.brush != null) background.isDark else null,
            )
        }

        infoFor?.let { message ->
            com.leo.imessage.ui.components.MessageInfoSheet(
                message = message,
                chat = chat,
                onDismiss = { infoFor = null },
                hazeState = hazeState,
                darkBase = if (background.brush != null) background.isDark else null,
            )
        }

        // Re-resolved from the live list each frame, so a tapback or an edit
        // made while the thread is open shows up in it.
        val root = threadRoot?.let { byId[it.id] ?: it }
        if (root != null) {
            com.leo.imessage.ui.components.ReplyThreadView(
                root = root,
                replies = messages.filter { it.replyToId == root.id },
                chat = chat,
                hazeState = hazeState,
                darkBase = if (background.brush != null) background.isDark else null,
                onDismiss = { threadRoot = null },
                onSendReply = { text, effect, attachments ->
                    onSend(text, effect, root.id, attachments)
                },
            )
        }

        viewing?.let { attachment ->
            com.leo.imessage.ui.components.MediaViewer(
                attachment = attachment,
                onDismiss = { viewing = null },
            )
        }

        val focused = menuFor
        com.leo.imessage.ui.components.MessageContextMenu(
            visible = focused != null,
            hazeState = hazeState,
            darkBase = if (background.brush != null) background.isDark else null,
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
                add(
                    com.leo.imessage.ui.components.MenuAction("Reply") {
                        replyingTo = focused?.message
                        composerFocus.requestFocus()
                        keyboard?.show()
                    }
                )
                add(
                    com.leo.imessage.ui.components.MenuAction("Copy") {
                        focused?.message?.let { m ->
                            val payload = m.text.ifBlank {
                                m.attachments.firstOrNull()?.fileName.orEmpty()
                            }
                            if (payload.isNotBlank()) {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(payload))
                            }
                        }
                    }
                )
                if (focused?.message?.isFromMe == true && focused.message.isUnsent.not()) {
                    add(
                        com.leo.imessage.ui.components.MenuAction("Edit") {
                            editingMessage = focused.message
                            composerFocus.requestFocus()
                            keyboard?.show()
                        }
                    )
                    add(
                        com.leo.imessage.ui.components.MenuAction("Undo Send", destructive = true) {
                            onUnsend(focused.message.id)
                        }
                    )
                }
                add(
                    com.leo.imessage.ui.components.MenuAction("Delete", destructive = true) {
                        focused?.let { onDelete(it.message.id) }
                    }
                )
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


