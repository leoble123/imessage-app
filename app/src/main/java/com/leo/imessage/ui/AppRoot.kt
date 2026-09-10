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
fun AppRoot(backend: MessagingBackend) {
    val chats by backend.chats.collectAsState(initial = remember { backend.chatsNow() })
    var openChatId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    // A photo opened from the details screen's Photos strip.
    var detailsViewing by remember { mutableStateOf<com.leo.imessage.data.Attachment?>(null) }
    // Per-chat background choice, kept for the session.
    val backgrounds = remember { mutableStateMapOf<String, String>() }
    val openChat = chats.firstOrNull { it.id == openChatId }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    // 0 = list fully shown, 1 = conversation fully shown.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(openChatId) {
        progress.animateTo(if (openChatId != null) 1f else 0f, Motion.standard())
    }

    BackHandler(
        enabled = openChatId != null || showSettings || showCompose ||
            showDetails || detailsViewing != null
    ) {
        when {
            detailsViewing != null -> detailsViewing = null
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
                onOpenChat = { chat -> openChatId = chat.id },
                onOpenSettings = { showSettings = true },
                onCompose = { showCompose = true },
                onSetPinned = { id, pinned -> scope.launch { backend.setPinned(id, pinned) } },
                onMarkUnread = { id -> scope.launch { backend.markUnread(id) } },
                onSetArchived = { id, archived ->
                    scope.launch {
                        if (openChatId == id && archived) openChatId = null
                        backend.setArchived(id, archived)
                    }
                },
                onSetMuted = { id, muted -> scope.launch { backend.setMuted(id, muted) } },
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

        if (openChat != null || progress.value > 0f) {
            val chat = openChat ?: chats.firstOrNull()
            if (chat != null) {
                val messages by remember(chat.id) { backend.messages(chat.id) }
                    .collectAsState(initial = remember(chat.id) { backend.messagesNow(chat.id) })

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = screenWidthPx * (1f - progress.value)
                            // A soft shadow along the leading edge, the way a
                            // pushed UIKit view casts onto the one beneath.
                            shadowElevation = 18f * progress.value
                        }
                ) {
                    ConversationScreen(
                        chat = chat,
                        messages = messages,
                        onBack = { openChatId = null },
                        onSend = { text, effect, replyToId, attachments ->
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
                        onOpenDetails = { showDetails = true },
                        onDelete = { messageId -> scope.launch { backend.delete(messageId) } },
                        onFaceTime = {
                            // No FaceTime on Android, so do the honest
                            // equivalent: dial the handle if it's a number,
                            // and otherwise fall back to the details screen
                            // rather than a button that does nothing.
                            val handle = chat.participants.firstOrNull()?.handle
                            val dialable = handle != null &&
                                handle.any { it.isDigit() } && !handle.contains('@')
                            if (dialable) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:$handle"),
                                        )
                                    )
                                }
                            } else {
                                showDetails = true
                            }
                        },
                        onEdit = { messageId, newText ->
                            scope.launch { backend.edit(messageId, newText) }
                        },
                        backgroundId = backgrounds[chat.id] ?: "none",
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
                    onSelectBackground = { backgrounds[openChat.id] = it },
                    onBack = { showDetails = false },
                    onSetMuted = { scope.launch { backend.setMuted(openChat.id, it) } },
                    onSetPinned = { scope.launch { backend.setPinned(openChat.id, it) } },
                    attachments = backend.messagesNow(openChat.id).flatMap { it.attachments },
                    onOpenAttachment = { detailsViewing = it },
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
                com.leo.imessage.ui.screens.SettingsScreen(onBack = { showSettings = false })
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
                    onBack = { showCompose = false },
                    onPick = { chat ->
                        showCompose = false
                        openChatId = chat.id
                    },
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
    }
}
