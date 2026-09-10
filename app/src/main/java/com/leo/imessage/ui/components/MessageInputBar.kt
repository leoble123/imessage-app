package com.leo.imessage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.MessageEffect
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import dev.chrisbanes.haze.HazeState

/**
 * The Messages composer.
 *
 * Layout matches iOS exactly, and the details are what make it read right:
 * the "+" sits *outside* the field, the send arrow sits *inside* it at the
 * trailing edge, the field is a stroked capsule rather than a filled box, and
 * it grows with the text up to a cap before scrolling internally.
 */
@Composable
fun MessageInputBar(
    onSend: (String, MessageEffect, List<com.leo.imessage.data.Attachment>) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    darkBase: Boolean? = null,
    editing: com.leo.imessage.data.Message? = null,
    onCancelEdit: () -> Unit = {},
    onCommitEdit: (String) -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** "iMessage" in a transcript, "Reply" inside a reply thread. */
    placeholder: String = "iMessage",
    /** Opens the keyboard as soon as the bar appears. */
    autoFocus: Boolean = false,
) {
    val palette = LocalPalette.current
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    var text by remember { mutableStateOf("") }
    val ownFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val focus = focusRequester ?: ownFocus
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            // One frame of grace: the FocusRequester isn't attached to a node
            // until the modifier has been applied, and requesting before that
            // throws rather than focusing.
            androidx.compose.runtime.withFrameNanos {}
            focus.requestFocus()
            keyboard?.show()
        }
    }

    // Entering edit mode loads the existing text so it can be changed in place.
    LaunchedEffect(editing?.id) {
        if (editing != null) text = editing.text
    }
    var showEffects by remember { mutableStateOf(false) }
    var showTray by remember { mutableStateOf(false) }
    // Staged attachments and effect, so you can line up a photo, type a
    // caption and pick an effect before anything is sent - rather than each
    // choice firing off a message of its own.
    var pending by remember { mutableStateOf<List<com.leo.imessage.data.Attachment>>(emptyList()) }
    var pendingEffect by remember { mutableStateOf(MessageEffect.NONE) }
    val canSend = text.isNotBlank() || pending.isNotEmpty()

    fun commit(effect: MessageEffect) {
        if (!canSend) return
        onSend(text.trim(), effect, pending)
        text = ""
        pending = emptyList()
        pendingEffect = MessageEffect.NONE
    }

    if (showEffects) {
        EffectPicker(
            onPick = { effect ->
                showEffects = false
                if (canSend) commit(effect) else pendingEffect = effect
            },
            onDismiss = { showEffects = false },
        )
    }

    if (showTray) {
        AttachmentTray(
            onAttach = { added -> pending = pending + added },
            onPickEffect = { effect -> pendingEffect = effect },
            onDismiss = { showTray = false },
        )
    }

    // A floating capsule rather than a bar welded to the bottom edge: the
    // transcript runs underneath it and blurs through, which is the entire
    // reason for using a real backdrop blur instead of a tinted fill.
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp)),
        hazeState = hazeState,
        tintAlpha = 0.5f,
        blurRadius = 36,
        darkBase = darkBase,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .border(
                    0.9.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (darkBase == false) 0.5f else 0.22f),
                            Color.White.copy(alpha = 0.05f),
                        )
                    ),
                    RoundedCornerShape(26.dp),
                )
        )
        // Height changes - the edit banner arriving, the field growing as
        // text wraps - flow instead of snapping.
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(Motion.fluid())
        ) {
        if (editing != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Editing message",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                    modifier = Modifier
                        .clickable {
                            text = ""
                            onCancelEdit()
                        }
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (pending.isNotEmpty() || pendingEffect != MessageEffect.NONE) {
            StagedRow(
                attachments = pending,
                effect = pendingEffect,
                onRemove = { att -> pending = pending.filterNot { it.id == att.id } },
                onClearEffect = { pendingEffect = MessageEffect.NONE },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PlusButton(
                expanded = showTray,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showTray = true
                },
            )

            Spacer(Modifier.width(8.dp))

            // Stroked capsule containing the field and the send button.
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, palette.separator.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(start = 13.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 28.dp, max = 120.dp)
                        .padding(top = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.tertiaryLabel,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.accent),
                        // Honours the Send with Return Key setting: with it
                        // off, Return inserts a newline as it should.
                        singleLine = false,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = if (settings.sendWithReturn) {
                                androidx.compose.ui.text.input.ImeAction.Send
                            } else {
                                androidx.compose.ui.text.input.ImeAction.Default
                            },
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (editing != null) {
                                    onCommitEdit(text.trim())
                                    text = ""
                                } else {
                                    commit(pendingEffect)
                                }
                            },
                        ),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Mic sits in the field until there's something to send,
                // then the send button takes its place. Both stay composed
                // and cross-dissolve through graphicsLayer, so the handover
                // is one continuous motion rather than two pops - and costs
                // no recomposition while it runs.
                val sendAppear = animateFloatAsState(
                    targetValue = if (canSend) 1f else 0f,
                    animationSpec = Motion.fluid(),
                    label = "sendAppear",
                )
                Box(
                    Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MicIcon(
                        color = palette.secondaryLabel,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer {
                                val a = (1f - sendAppear.value).coerceIn(0f, 1f)
                                alpha = a
                                val sc = 0.72f + 0.28f * a
                                scaleX = sc
                                scaleY = sc
                            },
                    )
                    Box(
                        Modifier.graphicsLayer {
                            val a = sendAppear.value.coerceIn(0f, 1f)
                            alpha = a
                            val sc = 0.7f + 0.3f * a
                            scaleX = sc
                            scaleY = sc
                            // Lifts into place as it arrives.
                            translationY = 4.dp.toPx() * (1f - a)
                        }
                    ) {
                        SendButton(
                            onSend = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (editing != null) {
                                    onCommitEdit(text.trim())
                                    text = ""
                                } else {
                                    commit(pendingEffect)
                                }
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showEffects = true
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PlusButton(expanded: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.88f, spec = Motion.bouncy(), label = "plusPress")
    // Turns into a close affordance while the tray is up, so the same
    // target both opens and shuts it.
    val turn = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = Motion.fluid(),
        label = "plusTurn",
    )
    Box(
        Modifier
            .size(32.dp)
            .scaleFrom(scale)
            .graphicsLayer { rotationZ = 45f * turn.value }
            .clip(CircleShape)
            .background(palette.fieldBackground)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Attach",
            tint = palette.secondaryLabel,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SendButton(onSend: () -> Unit, onLongPress: () -> Unit) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.86f, spec = Motion.bouncy(), label = "sendPress")
    Box(
        Modifier
            .size(28.dp)
            .scaleFrom(scale)
            .clip(CircleShape)
            .background(palette.outgoingBubble)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onSend() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = "Send",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun MicIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.10f),
            size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.20f, h * 0.34f),
            size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.42f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.76f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.92f),
            strokeWidth = w * 0.09f,
        )
    }
}

/** Send-effect chooser, reached by long-pressing send. */
@Composable
fun EffectPicker(
    onPick: (MessageEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val options = listOf(
        MessageEffect.SLAM to "Slam",
        MessageEffect.LOUD to "Loud",
        MessageEffect.GENTLE to "Gentle",
        MessageEffect.INVISIBLE_INK to "Invisible Ink",
        MessageEffect.NONE to "Send without effect",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                    color = if (effect == MessageEffect.NONE) palette.secondaryLabel else palette.accent,
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

/**
 * What's queued up but not sent yet.
 *
 * Attachments show as removable thumbnails and the chosen effect as a chip,
 * because a staged effect with nothing on screen to show for it is a message
 * that arrives with a screen animation you don't remember asking for.
 */
@Composable
private fun StagedRow(
    attachments: List<com.leo.imessage.data.Attachment>,
    effect: MessageEffect,
    onRemove: (com.leo.imessage.data.Attachment) -> Unit,
    onClearEffect: () -> Unit,
) {
    val palette = LocalPalette.current
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (effect != MessageEffect.NONE) {
            item(key = "effect") {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClearEffect() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sent with ${effect.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                    Text(
                        "  \u00d7",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent,
                    )
                }
            }
        }

        items(attachments, key = { it.id }) { att ->
            Box {
                val thumb = rememberThumbnail(att.uri, att.kind, maxPx = 220)
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.fieldBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        androidx.compose.foundation.Image(
                            bitmap = thumb,
                            contentDescription = att.fileName,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    } else {
                        Text(
                            text = com.leo.imessage.media.MediaTools.iconLabelFor(att.kind),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryLabel,
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRemove(att) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "\u00d7",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
