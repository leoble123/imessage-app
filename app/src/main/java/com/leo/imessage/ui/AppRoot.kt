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
    val chats by backend.chats.collectAsState(initial = emptyList())
    var openChatId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    val openChat = chats.firstOrNull { it.id == openChatId }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    // 0 = list fully shown, 1 = conversation fully shown.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(openChatId) {
        progress.animateTo(if (openChatId != null) 1f else 0f, Motion.standard())
    }

    BackHandler(enabled = openChatId != null || showSettings || showCompose) {
        when {
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
                    .collectAsState(initial = emptyList())

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
                        onSend = { text, effect ->
                            scope.launch { backend.send(chat.id, text, effect) }
                        },
                        onTapback = { messageId, kind ->
                            scope.launch { backend.setTapback(messageId, kind) }
                        },
                    )
                }
            }
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
