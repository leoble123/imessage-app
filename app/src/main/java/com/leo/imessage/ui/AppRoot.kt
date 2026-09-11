package com.leo.imessage.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.leo.imessage.data.Chat
import com.leo.imessage.data.MessagingBackend
import com.leo.imessage.ui.screens.ChatListScreen
import com.leo.imessage.ui.screens.ConversationScreen
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Two-screen stack with a UIKit-style push transition and an interactive
 * edge-swipe back gesture.
 *
 * Rather than using navigation-compose's transitions, this drives the offset
 * directly: the incoming screen slides in from the right while the outgoing
 * one parallaxes a third of the way and dims - and crucially, dragging from
 * the left edge scrubs that same animation with your finger instead of
 * playing a canned one.
 */
@Composable
fun AppRoot(
    backend: MessagingBackend,
    /** Conversation to jump straight into, from a tapped notification. */
    openChatRequest: String? = null,
    onChatRequestHandled: () -> Unit = {},
    /** Shown in Settings. Null when the app is running on sample data. */
    accountSummary: com.leo.imessage.data.AccountSummary? = null,
    onSignOut: () -> Unit = {},
    /** The phone's address book, for the New Message screen. */
    addressBook: List<com.leo.imessage.data.Contacts.SavedContact> = emptyList(),
    /** Imports an OpenBubbles export, returning a line about what it found. */
    onImport: (suspend (android.net.Uri) -> String)? = null,
    /** Places a FaceTime call. Null when there's no live account behind it. */
    onPlaceCall: ((List<String>) -> Unit)? = null,
) {
    val chats by backend.chats.collectAsState(initial = remember { backend.chatsNow() })
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var openChatId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showReleaseNotes by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    var composeError by remember { mutableStateOf<String?>(null) }
    // Whether the picker is choosing someone to message or to call.
    var composeMode by remember {
        mutableStateOf(com.leo.imessage.ui.screens.PickMode.MESSAGE)
    }
    var showDetails by remember { mutableStateOf(false) }
    // A photo opened from the details screen's Photos strip.
    var detailsViewing by remember { mutableStateOf<com.leo.imessage.data.Attachment?>(null) }
    // Per-chat background choice, kept for the session.
    val store = remember(context) { com.leo.imessage.ui.theme.SettingsStore(context) }
    val backgrounds = remember {
        mutableStateMapOf<String, String>().apply { putAll(store.backgrounds()) }
    }
    // What changed in the build that just replaced the one you had, if this
    // is the first launch of it. Read once, so acknowledging it can't make
    // the card come back on the next recomposition.
    var whatsNew by remember {
        mutableStateOf(
            com.leo.imessage.util.Changelog
                .current(com.leo.imessage.BuildConfig.VERSION_CODE)
                ?.takeIf { store.lastSeenVersion() != it.versionCode }
        )
    }
    // Unsent text per conversation, so leaving a thread mid-sentence and
    // coming back finds the sentence still there.
    val drafts = remember { mutableStateMapOf<String, String>() }
    val openChat = chats.firstOrNull { it.id == openChatId }

    val scope = rememberCoroutineScope()
    // Tells the other side we're typing. Debounced, because the wire message
    // is a real one and firing it per keystroke is a burst of traffic.
    val typing = remember(backend) {
        com.leo.imessage.data.TypingReporter(backend)
    }
    val density = LocalDensity.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    LaunchedEffect(openChatRequest) {
        val requested = openChatRequest ?: return@LaunchedEffect
        if (chats.any { it.id == requested }) {
            showSettings = false
            showCompose = false
            showDetails = false
            openChatId = requested
        }
        onChatRequestHandled()
    }

    // Notifications are posted by ConnectionService, not here: this
    // composition only runs while the app is on screen, which is precisely
    // when a notification is least needed. What the UI contributes is which
    // thread is open, so the service can stay quiet about that one.
    LaunchedEffect(openChatId) {
        // Leaving a conversation has to clear the bubble too, or it sits on
        // their screen until the timeout - or forever, if the app is closed.
        if (openChatId == null) typing.stop()
        // The backend needs this too, so a message arriving in the thread
        // you're reading doesn't raise a badge you can't clear.
        (backend as? com.leo.imessage.data.RustBackend)?.openChatId = openChatId
        (context.applicationContext as? com.leo.imessage.EchoApp)?.visibleChatId = openChatId
        openChatId?.let { com.leo.imessage.notify.Notifier.clear(context, it) }
    }

    // 0 = list fully shown, 1 = conversation fully shown.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(openChatId) {
        progress.animateTo(if (openChatId != null) 1f else 0f, Motion.standard())
    }

    BackHandler(
        enabled = openChatId != null || showSettings || showCompose ||
            showDetails || detailsViewing != null || showReleaseNotes
    ) {
        when {
            detailsViewing != null -> detailsViewing = null
            showReleaseNotes -> showReleaseNotes = false
            showDetails -> showDetails = false
            showCompose -> showCompose = false
            showSettings -> showSettings = false
            else -> openChatId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Chat list, parallaxing back as the conversation covers it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -screenWidthPx * 0.32f * progress.value
                }
        ) {
            ChatListScreen(
                chats = chats,
                drafts = drafts,
                onOpenChat = { chat -> openChatId = chat.id },
                onOpenSettings = { showSettings = true },
                onCompose = {
                    composeMode = com.leo.imessage.ui.screens.PickMode.MESSAGE
                    showCompose = true
                },
                onFaceTime = onPlaceCall?.let {
                    {
                        composeMode = com.leo.imessage.ui.screens.PickMode.CALL
                        showCompose = true
                    }
                },
                onSetPinned = { id, pinned -> scope.launch { backend.setPinned(id, pinned) } },
                onMarkUnread = { id -> scope.launch { backend.markUnread(id) } },
                onSetArchived = { id, archived ->
                    scope.launch {
                        if (openChatId == id && archived) openChatId = null
                        backend.setArchived(id, archived)
                    }
                },
                onSetMuted = { id, muted -> scope.launch { backend.setMuted(id, muted) } },
                onMarkAllRead = {
                    scope.launch {
                        // Sequential, not a parallel fan-out: each of these
                        // sends a read receipt over the same connection, and
                        // firing twenty at once is a burst Apple has no
                        // reason to expect from one person opening an app.
                        chats.filter { it.unreadCount > 0 }
                            .forEach { runCatching { backend.markRead(it.id) } }
                    }
                },
                onDeleteChat = { id ->
                    scope.launch {
                        if (openChatId == id) openChatId = null
                        backend.deleteChat(id)
                    }
                },
            )
            if (progress.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f * progress.value))
                )
            }
        }

        // The chat stays on screen for the whole exit animation. Falling back
        // to chats.firstOrNull() here is what caused the flash on the way
        // out: the moment you hit back, openChat went null and the screen
        // sliding away re-rendered as somebody else's conversation.
        var lastOpenChat by remember { mutableStateOf<Chat?>(null) }
        LaunchedEffect(openChat?.id) { if (openChat != null) lastOpenChat = openChat }

        if (openChat != null || progress.value > 0f) {
            val chat = openChat ?: lastOpenChat
            if (chat != null) {
                val messages by remember(chat.id) { backend.messages(chat.id) }
                    .collectAsState(initial = remember(chat.id) { backend.recentMessages(chat.id) })

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress.value
                            translationX = screenWidthPx * (1f - p)
                            shadowElevation = 18f * p

                            // Liquid-glass push: the screen arrives slightly
                            // small with rounded corners and swells flat as it
                            // lands, so it reads as a droplet pulling into
                            // shape rather than a card sliding across. The
                            // corner radius is what sells it - a hard-edged
                            // rectangle sliding in is a slide no matter what
                            // else it does.
                            val settle = p * p * (3f - 2f * p)
                            val scale = 0.94f + 0.06f * settle
                            scaleX = scale
                            scaleY = scale
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                (34.dp.toPx() * (1f - settle))
                            )
                            clip = true
                        }
                ) {
                    ConversationScreen(
                        chat = chat,
                        messages = messages,
                        onBack = { openChatId = null },
                        onSend = { text, effect, replyToId, attachments ->
                            // The draft is gone the moment this fires, so the
                            // typing bubble has to go with it.
                            typing.stop()
                            scope.launch {
                                backend.send(chat.id, text, effect, replyToId, attachments)
                            }
                        },
                        onTapback = { messageId, kind ->
                            scope.launch { backend.setTapback(messageId, kind) }
                        },
                        onEmojiTapback = { messageId, emoji ->
                            scope.launch { backend.setEmojiTapback(messageId, emoji) }
                        },
                        onUnsend = { messageId ->
                            scope.launch { backend.unsend(messageId) }
                        },
                        onMarkRead = { scope.launch { backend.markRead(chat.id) } },
                        onRetry = { id -> scope.launch { backend.retry(id) } },
                        onSetBookmarked = { id, v ->
                            scope.launch { backend.setBookmarked(id, v) }
                        },
                        onSetMessagePinned = { id, v ->
                            scope.launch { backend.setMessagePinned(id, v) }
                        },
                        onSetNote = { id, note -> scope.launch { backend.setNote(id, note) } },
                        onSetReminder = { id, at -> scope.launch { backend.setReminder(id, at) } },
                        onScheduleSend = { text, at ->
                            scope.launch { backend.scheduleSend(chat.id, text, at) }
                        },
                        onResolveScheduled = { id, send ->
                            scope.launch { backend.resolveScheduled(id, send) }
                        },
                        onSendPoll = { q, opts ->
                            scope.launch { backend.sendPoll(chat.id, q, opts) }
                        },
                        onVotePoll = { optionId ->
                            scope.launch {
                                // The vote arrives with only the option id, so
                                // find the poll that owns it.
                                backend.messagesNow(chat.id)
                                    .firstOrNull { m ->
                                        m.poll?.options?.any { it.id == optionId } == true
                                    }
                                    ?.let { backend.votePoll(it.id, optionId) }
                            }
                        },
                        onShareLocation = {
                            scope.launch {
                                val link = com.leo.imessage.media.currentLocationLink(context)
                                backend.send(
                                    chatId = chat.id,
                                    text = link ?: "Couldn't get a location fix",
                                )
                            }
                        },
                        onOpenDetails = { showDetails = true },
                        onDelete = { messageId -> scope.launch { backend.delete(messageId) } },
                        onFaceTime = {
                            // A real FaceTime call now, rather than handing
                            // the number to the phone dialler.
                            val targets = chat.participants.map { it.handle }
                            if (targets.isNotEmpty() && onPlaceCall != null) {
                                onPlaceCall(targets)
                            } else {
                                showDetails = true
                            }
                        },
                        onEdit = { messageId, newText ->
                            scope.launch { backend.edit(messageId, newText) }
                        },
                        backgroundId = backgrounds[chat.id] ?: "none",
                        draft = drafts[chat.id].orEmpty(),
                        onDraftChange = { text ->
                            if (text.isBlank()) drafts.remove(chat.id)
                            else drafts[chat.id] = text
                            typing.onTyping(chat.id, text.isNotBlank())
                        },
                    )
                }
            }
        }

        // Conversation details push over the thread.
        val detailsProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showDetails) 1f else 0f,
            animationSpec = Motion.standard(),
            label = "detailsPush",
        )
        if (detailsProgress > 0.001f && openChat != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = screenWidthPx * (1f - detailsProgress)
                        shadowElevation = 18f * detailsProgress
                    }
            ) {
                com.leo.imessage.ui.screens.ChatDetailsScreen(
                    chat = openChat,
                    selectedBackgroundId = backgrounds[openChat.id] ?: "none",
                    onSelectBackground = {
                        backgrounds[openChat.id] = it
                        store.putBackgrounds(backgrounds.toMap())
                    },
                    onBack = { showDetails = false },
                    onSetMuted = { scope.launch { backend.setMuted(openChat.id, it) } },
                    onSetPinned = { scope.launch { backend.setPinned(openChat.id, it) } },
                    attachments = backend.messagesNow(openChat.id).flatMap { it.attachments },
                    onOpenAttachment = { detailsViewing = it },
                    onExport = {
                        val transcript = com.leo.imessage.util.exportTranscript(
                            openChat,
                            backend.messagesNow(openChat.id),
                        )
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_SEND
                                    ).apply {
                                        type = "text/plain"
                                        putExtra(
                                            android.content.Intent.EXTRA_SUBJECT,
                                            openChat.displayName,
                                        )
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            transcript,
                                        )
                                    },
                                    "Export Conversation",
                                ).apply {
                                    addFlags(
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    )
                                }
                            )
                        }
                    },
                )
            }
        }

        detailsViewing?.let { attachment ->
            com.leo.imessage.ui.components.MediaViewer(
                attachment = attachment,
                onDismiss = { detailsViewing = null },
            )
        }

        // Settings pushes over everything with the same slide-in.
        val settingsProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showSettings) 1f else 0f,
            animationSpec = Motion.standard(),
            label = "settingsPush",
        )
        if (settingsProgress > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = screenWidthPx * (1f - settingsProgress)
                        shadowElevation = 18f * settingsProgress
                    }
            ) {
                com.leo.imessage.ui.screens.SettingsScreen(
                    onBack = { showSettings = false },
                    account = accountSummary,
                    onSignOut = onSignOut,
                    onImport = onImport,
                    onOpenReleaseNotes = { showReleaseNotes = true },
                )
            }
        }

        // Release Notes pushes on top of Settings, the way a sub-screen does.
        val notesProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showReleaseNotes) 1f else 0f,
            animationSpec = Motion.standard(),
            label = "notesPush",
        )
        if (notesProgress > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = screenWidthPx * (1f - notesProgress)
                        shadowElevation = 18f * notesProgress
                    }
            ) {
                com.leo.imessage.ui.screens.ReleaseNotesScreen(
                    onBack = { showReleaseNotes = false },
                )
            }
        }

        // New Message presents modally, sliding up from the bottom.
        val composeProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (showCompose) 1f else 0f,
            animationSpec = Motion.gentle(),
            label = "composePresent",
        )
        if (composeProgress > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = size.height * (1f - composeProgress)
                        shadowElevation = 20f * composeProgress
                    }
            ) {
                com.leo.imessage.ui.screens.ComposeScreen(
                    chats = chats,
                    onBack = { showCompose = false; composeError = null },
                    onPick = { chat ->
                        showCompose = false
                        composeError = null
                        openChatId = chat.id
                    },
                    onStartNew = { handle ->
                        if (composeMode == com.leo.imessage.ui.screens.PickMode.CALL) {
                            showCompose = false
                            composeError = null
                            onPlaceCall?.invoke(listOf(handle))
                            return@ComposeScreen
                        }
                        scope.launch {
                            try {
                                val id = backend.startChat(listOf(handle))
                                showCompose = false
                                composeError = null
                                openChatId = id
                            } catch (e: Throwable) {
                                // Throwable, not Exception. A missing class or
                                // a failed static initialiser arrives as an
                                // Error, which Exception does not catch - so
                                // it escaped the coroutine and took the whole
                                // app down instead of showing a message here.
                                composeError = buildString {
                                    append(e::class.java.simpleName)
                                    e.message?.let { append(": ").append(it) }
                                }
                                android.util.Log.e("AppRoot", "startChat failed", e)
                            }
                        }
                    },
                    error = composeError,
                    addressBook = addressBook,
                    mode = composeMode,
                )
            }
        }

        // Left-edge grab area for the interactive back gesture.
        if (openChatId != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .align(Alignment.CenterStart)
                    .pointerInput(openChatId) {
                        var committed = false
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (progress.value < 0.62f) {
                                        progress.animateTo(0f, Motion.snappy())
                                        openChatId = null
                                    } else {
                                        progress.animateTo(1f, Motion.snappy())
                                    }
                                    committed = false
                                }
                            },
                            onDragCancel = {
                                scope.launch { progress.animateTo(1f, Motion.snappy()) }
                            },
                        ) { _, dragAmount ->
                            scope.launch {
                                val next = (progress.value - dragAmount / screenWidthPx)
                                    .coerceIn(0f, 1f)
                                if (next < 0.62f && !committed) {
                                    committed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (next > 0.62f) {
                                    committed = false
                                }
                                progress.snapTo(next)
                            }
                        }
                    }
            )
        }

        // Above everything, including the conversation and Settings: this is
        // the first thing the build has to say, and it says it once.
        whatsNew?.let { release ->
            com.leo.imessage.ui.components.WhatsNewSheet(
                release = release,
                onSeeAll = {
                    showSettings = true
                    showReleaseNotes = true
                },
                onDismiss = {
                    store.markVersionSeen(release.versionCode)
                    whatsNew = null
                },
            )
        }
    }
}
