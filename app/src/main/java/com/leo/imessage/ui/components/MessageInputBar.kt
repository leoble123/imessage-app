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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.font.FontWeight
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
    onSend: (String, MessageEffect, List<com.leo.imessage.data.Attachment>, List<String>) -> Unit,
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
    /** True while the attachment tray is showing, so "+" reads as a close. */
    trayOpen: Boolean = false,
    onToggleTray: () -> Unit = {},
    /** The message this reply is aimed at, shown as a banner above the field. */
    replyingTo: com.leo.imessage.data.Message? = null,
    onCancelReply: () -> Unit = {},
    /** Text kept for this conversation while you were elsewhere. */
    draft: String = "",
    draftKey: String = "",
    onDraftChange: (String) -> Unit = {},
    /** Tapping the mic hands off to the recorder rather than sending. */
    onRecordAudio: () -> Unit = {},
    /** Long-pressing send with text queues it instead. */
    onSendLater: (String) -> Unit = {},
    /** Attachments staged by the tray, hoisted so the tray can outlive this. */
    staged: List<com.leo.imessage.data.Attachment> = emptyList(),
    onRemoveStaged: (com.leo.imessage.data.Attachment) -> Unit = {},
    stagedEffect: MessageEffect = MessageEffect.NONE,
    onClearStagedEffect: () -> Unit = {},
    /** Who can be mentioned here. Empty in a one-to-one, where @ means nothing. */
    participants: List<com.leo.imessage.data.Contact> = emptyList(),
) {
    val palette = LocalPalette.current
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val haptics = com.leo.imessage.ui.components.rememberHaptics()
    // Keyed on the conversation, so switching threads loads that thread's
    // draft rather than carrying the last one across.
    var text by remember(draftKey) { mutableStateOf(draft) }
    // The last draft this bar told the caller about.
    //
    // The field owns its own text and only seeded it from `draft` once, keyed
    // on the conversation - so anything that set the draft from outside was
    // silently dropped, and the composer just sat there. That is why tapping a
    // Quick Reply did nothing at all: it wrote to the draft the bar had
    // stopped listening to.
    //
    // Comparing against what was last reported is what makes adopting safe. A
    // draft that differs came from somewhere else and should be taken; one
    // that matches is this bar's own echo arriving back, and adopting that on
    // every keystroke would fight the person typing.
    var lastReported by remember(draftKey) { mutableStateOf(draft) }

    fun report(value: String) {
        lastReported = value
        onDraftChange(value)
    }

    LaunchedEffect(draft, draftKey) {
        if (draft != lastReported) {
            text = draft
            lastReported = draft
        }
    }
    val ownFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val focus = focusRequester ?: ownFocus
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

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

    // Handles this draft mentions, remembered as they are inserted rather than
    // recovered from the text later. A name that merely appears in a sentence
    // is not a mention of that person and must not ping them.
    val mentioned = remember(draftKey) { mutableStateListOf<String>() }
    // The "@..." being typed at the end of the draft, if there is one. Only at
    // the end: that is where a composer without a cursor position can be sure
    // the token belongs, and guessing mid-sentence gets it wrong.
    val mentionQuery = remember(text) {
        if (participants.size < 2) null
        else Regex("@([\\p{L}' -]{0,24})$").find(text)?.groupValues?.get(1)
    }
    // Staged attachments and effect, so you can line up a photo, type a
    // caption and pick an effect before anything is sent - rather than each
    // choice firing off a message of its own.
    val pending = staged
    val pendingEffect = stagedEffect
    val canSend = text.isNotBlank() || pending.isNotEmpty()

    fun commit(effect: MessageEffect) {
        if (!canSend) return
        onSend(text.trim(), effect, pending, mentioned.toList())
        text = ""
        mentioned.clear()
        report("")
    }

    if (showEffects) {
        EffectPicker(
            onPick = { effect ->
                showEffects = false
                if (canSend) commit(effect)
            },
            onDismiss = { showEffects = false },
            hazeState = hazeState,
            darkBase = darkBase,
        )
    }


    // Two independent pieces of glass, not one slab: the "+" is its own
    // circle and the field its own capsule, each blurring the thread behind
    // it separately. A single container welds them together and the whole
    // thing reads as one bar again - the separation is what makes them feel
    // like objects floating on the conversation rather than chrome bolted
    // to the bottom of it. Swiping either one vertically summons or
    // dismisses the keyboard.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .animateContentSize(Motion.fluid())
            .verticalFlick(
                onUp = {
                    focus.requestFocus()
                    keyboard?.show()
                },
                onDown = {
                    if (trayOpen) {
                        onToggleTray()
                    } else {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
            ),
    ) {
        run {
        if (mentionQuery != null) {
            val prefix = mentionQuery
            val matches = participants.filter {
                prefix.isBlank() || it.displayName.startsWith(prefix, ignoreCase = true)
            }

            fun insert(names: List<com.leo.imessage.data.Contact>) {
                if (names.isEmpty()) return
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // Replace the "@partial" that is being typed, rather than
                // appending beside it.
                val head = text.dropLast(prefix.length + 1)
                text = head + names.joinToString(" ") { "@" + it.displayName } + " "
                names.forEach { if (it.handle !in mentioned) mentioned.add(it.handle) }
                report(text)
            }

            if (matches.isNotEmpty() || "everyone".startsWith(prefix, ignoreCase = true)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // Everyone first, and only worth offering in a group.
                    //
                    // iMessage has no "notify all" of its own - a mention names
                    // one person. What it has is the rule that a mention of you
                    // reaches you through a muted thread, so this expands into
                    // a real mention of each person rather than pretending to
                    // be a broadcast. Every phone in the group lights up, which
                    // is the point, and each name is visible before it sends.
                    if (participants.size > 1 && "everyone".startsWith(prefix, ignoreCase = true)) {
                        MentionRow(
                            label = "Everyone",
                            detail = "${participants.size} people",
                            accent = true,
                        ) { insert(participants) }
                    }
                    matches.take(6).forEach { person ->
                        MentionRow(
                            label = person.displayName,
                            detail = com.leo.imessage.data.Handles.display(person.handle),
                            accent = false,
                        ) { insert(listOf(person)) }
                    }
                }
            }
        }
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
        if (replyingTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.accent)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Replying to",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                    Text(
                        replyingTo.text.ifBlank { "Attachment" },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "\u00d7",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.tertiaryLabel,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onCancelReply() }
                        .padding(horizontal = 8.dp),
                )
            }
        }
        if (pending.isNotEmpty() || pendingEffect != MessageEffect.NONE) {
            StagedRow(
                attachments = pending,
                effect = pendingEffect,
                onRemove = onRemoveStaged,
                onClearEffect = onClearStagedEffect,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PlusButton(
                expanded = trayOpen,
                hazeState = hazeState,
                darkBase = darkBase,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleTray()
                },
            )

            Spacer(Modifier.width(8.dp))

            // The field is its own pane of glass with its own lit rim.
            GlassPill(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(21.dp)),
                hazeState = hazeState,
                darkBase = darkBase,
            ) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        0.9.dp,
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (darkBase == false) 0.55f else 0.24f),
                                Color.White.copy(alpha = 0.06f),
                            )
                        ),
                        RoundedCornerShape(21.dp),
                    )
            )
            Row(
                Modifier
                    .fillMaxWidth()
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
                            color = palette.secondaryLabel,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            report(it)
                        },
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

                // Mic and send occupy the same spot and cross-dissolve
                // between them, but there is only ever ONE hit target and it
                // decides what to do from `canSend`. Layering two live
                // buttons and hiding one with alpha is what made the mic
                // fire the send button underneath it - an alpha of zero
                // hides a control from your eyes, not from your finger.
                val sendAppear = animateFloatAsState(
                    targetValue = if (canSend) 1f else 0f,
                    animationSpec = Motion.fluid(),
                    label = "sendAppear",
                )
                var actionPressed by remember { mutableStateOf(false) }
                val actionScale = pressScale(actionPressed, pressedScale = 0.86f)

                Box(
                    Modifier
                        .size(30.dp)
                        .scaleFrom(actionScale)
                        .pointerInput(canSend, editing?.id) {
                            detectTapGestures(
                                onPress = {
                                    actionPressed = true
                                    tryAwaitRelease()
                                    actionPressed = false
                                },
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when {
                                        editing != null -> {
                                            onCommitEdit(text.trim())
                                            text = ""
                                        }
                                        canSend -> commit(pendingEffect)
                                        else -> onRecordAudio()
                                    }
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when {
                                        text.isNotBlank() -> {
                                            onSendLater(text.trim())
                                            text = ""
                                            report("")
                                        }
                                        canSend -> showEffects = true
                                        else -> onRecordAudio()
                                    }
                                },
                            )
                        },
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
                        Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                val a = sendAppear.value.coerceIn(0f, 1f)
                                alpha = a
                                val sc = 0.7f + 0.3f * a
                                scaleX = sc
                                scaleY = sc
                                translationY = 4.dp.toPx() * (1f - a)
                            }
                            .clip(CircleShape)
                            .background(palette.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun PlusButton(
    expanded: Boolean,
    hazeState: HazeState,
    darkBase: Boolean?,
    onClick: () -> Unit,
) {
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
    GlassPill(
        modifier = Modifier
            .size(34.dp)
            .scaleFrom(scale)
            .graphicsLayer { rotationZ = 45f * turn.value }
            .clip(CircleShape)
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
        hazeState = hazeState,
        darkBase = darkBase,
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Attach",
                tint = palette.secondaryLabel,
                modifier = Modifier.size(20.dp),
            )
        }
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
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
) {
    val palette = LocalPalette.current
    val options = listOf(
        MessageEffect.SLAM to "Slam",
        MessageEffect.LOUD to "Loud",
        MessageEffect.GENTLE to "Gentle",
        MessageEffect.INVISIBLE_INK to "Invisible Ink",
        MessageEffect.NONE to "Send without effect",
    )

    val progress = rememberPanelProgress(true) ?: return

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )

        GlassSheet(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    val sc = 0.9f + 0.1f * p
                    scaleX = sc
                    scaleY = sc
                },
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.82f,
        ) {
        Column {
            options.forEachIndexed { i, (effect, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    // Label colour, not accent: a pale blue on a pale panel
                    // is the exact combination that vanished.
                    color = if (effect == MessageEffect.NONE) palette.secondaryLabel
                        else palette.label,
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

/** One line of the mention picker. */
@Composable
private fun MentionRow(
    label: String,
    detail: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) palette.accent else palette.label,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryLabel,
            maxLines = 1,
        )
    }
}
