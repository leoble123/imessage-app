package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.LocalSettings
import com.leo.imessage.ui.theme.chatRingColorsFor
import com.leo.imessage.ui.theme.chatTintFor

/**
 * Pinned conversations, as circles above the list.
 *
 * Messages keeps up to nine of these and sizes them by how many there are -
 * one or two get big circles, a full set gets small ones - which is what
 * stops a single pin looking lost and nine looking cramped.
 *
 * Where this departs from Messages is that the circles carry state. iOS's
 * pins are an ornament: a static disc, a dot, and a tag that floats past. All
 * the information that would make them worth looking at is already here -
 * who is typing, what is unread, what just arrived - and none of it was on
 * screen. So the ring is the display: it breathes while they type, holds a
 * steady coloured arc while something is unread, and sits invisible when
 * there is nothing to say. Nothing moves unless something is actually
 * happening, which is the only way a moving thing stays informative.
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
    avatarSize: Dp,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = rememberHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.9f)
    val tint = chatTintFor(chat.avatarSeed)

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
        Box(contentAlignment = Alignment.Center) {
            LiveRing(
                chat = chat,
                diameter = avatarSize + RING_INSET * 2,
            )
            if (chat.isGroup) {
                GroupAvatar(chat.participants, avatarSize)
            } else {
                Avatar(chat.participants.first(), avatarSize)
            }
            UnreadLevel(chat = chat, diameter = avatarSize, tint = tint)
            if (chat.unreadCount > 0) {
                // The count itself once there is more than one. A dot says
                // "something"; a number says whether it can wait.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(50))
                        .background(tint)
                        .padding(horizontal = if (chat.unreadCount > 1) 5.dp else 0.dp)
                        .size(
                            width = if (chat.unreadCount > 1) Dp.Unspecified else 13.dp,
                            height = 13.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chat.unreadCount > 1) {
                        Text(
                            text = if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
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
            color = if (chat.unreadCount > 0) palette.label else palette.secondaryLabel,
            fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** How far the ring sits outside the avatar. */
private val RING_INSET = 5.dp

/**
 * The ring around a pinned avatar, and the whole reason these are worth
 * looking at.
 *
 * Three states, and only three, because a signal you have to decode is not a
 * signal: breathing means they are typing right now, a solid arc means there
 * is something unread, and nothing at all means nothing is happening. The
 * arc is drawn as a sweep rather than a full circle so that the two states
 * are told apart by shape and not only by movement - which is what keeps it
 * readable with Reduce Motion on, when the breathing is frozen.
 */
@Composable
private fun LiveRing(chat: Chat, diameter: Dp) {
    val settings = LocalSettings.current
    val colors = chatRingColorsFor(chat.avatarSeed)
    val typing = chat.isTyping
    val unread = chat.unreadCount > 0
    if (!typing && !unread) return

    val pulse = if (typing && !settings.lowPowerAnimations) {
        val transition = rememberInfiniteTransition(label = "ring")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // Close to a resting breath. Anything quicker reads as an
                // alert, and an alert that never stops is just noise.
                animation = tween(1400, easing = com.leo.imessage.ui.theme.Motion.AppleEase),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ringPulse",
        ).value
    } else {
        // Held at the top of the breath, so a frozen ring still reads as lit
        // rather than as half-drawn.
        if (typing) 1f else 0f
    }

    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                val stroke = 2.4.dp.toPx() + (if (typing) 1.4.dp.toPx() * pulse else 0f)
                val inset = stroke / 2f
                val brush = Brush.sweepGradient(
                    colors = colors + colors.first(),
                    center = center,
                )
                if (typing) {
                    // A full ring: they are here, the whole circle is theirs.
                    drawCircle(
                        brush = brush,
                        radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke),
                        alpha = 0.55f + 0.45f * pulse,
                    )
                } else {
                    // An arc, opening at the top-left where the unread badge
                    // sits, so the badge reads as the end of the arc rather
                    // than as something dropped on top of it.
                    drawArc(
                        brush = brush,
                        startAngle = 150f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                        alpha = 0.9f,
                    )
                }
            }
    )
}

/**
 * The pinned circles, shrunk into the navigation bar.
 *
 * The list's pins scroll away like everything else; this is what they scroll
 * *into*. Where the large title was is where they end up, so the screen's
 * heading becomes the people on it - which is the trade a messaging app
 * should obviously make and iOS never does, because it spends that row
 * re-printing the word "Messages" over a list of messages.
 *
 * Same rings, same badges, smaller. Deliberately not a second design: the
 * whole effect depends on these reading as the same objects that were just
 * above the list.
 */
@Composable
fun PinnedDock(
    pinned: List<Chat>,
    onOpen: (Chat) -> Unit,
    onLongPress: (Chat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinned.isEmpty()) return
    val haptics = rememberHaptics()

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pinned.take(9).forEach { chat ->
            var pressed by remember(chat.id) { mutableStateOf(false) }
            val scale = pressScale(pressed, pressedScale = 0.88f)
            Box(
                Modifier
                    .scaleFrom(scale)
                    .pointerInput(chat.id) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onOpen(chat) },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress(chat)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                LiveRing(chat = chat, diameter = DOCK_AVATAR + 6.dp)
                if (chat.isGroup) {
                    GroupAvatar(chat.participants, DOCK_AVATAR)
                } else {
                    Avatar(chat.participants.first(), DOCK_AVATAR)
                }
                if (chat.unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(chatTintFor(chat.avatarSeed))
                    )
                }
            }
        }
    }
}

private val DOCK_AVATAR = 34.dp

/**
 * Unread, as a level the circle fills to rather than a badge stuck on it.
 *
 * A dot answers "is there something". This answers "how much", which is the
 * question you actually have when you glance at a row of faces - one message
 * waiting and eleven waiting should not look the same, and on iOS they do.
 *
 * The surface is drawn with a shallow wave so it reads as liquid sitting at
 * rest rather than as a bar chart cropped to a circle, and it is clipped to
 * the avatar so the avatar is still what you recognise. Ten messages fills
 * it; past that the wave is the whole circle and the count on the badge is
 * doing the talking.
 */
@Composable
private fun UnreadLevel(chat: Chat, diameter: Dp, tint: Color) {
    if (chat.unreadCount <= 0) return
    val settings = LocalSettings.current
    val level = (chat.unreadCount / 10f).coerceIn(0.18f, 1f)

    // Settles into place rather than appearing at it, which is most of why it
    // reads as something poured in.
    val filled = remember { Animatable(0f) }
    LaunchedEffect(level) { filled.animateTo(level, com.leo.imessage.ui.theme.Motion.gentle()) }

    // A slow swell, so the surface is never quite flat. Frozen under Reduce
    // Motion, where a level is still a level.
    val sway = if (settings.lowPowerAnimations) {
        null
    } else {
        rememberInfiniteTransition(label = "unreadLevel").animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
            label = "unreadSway",
        )
    }

    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .drawBehind {
                val height = size.height
                val surface = height * (1f - filled.value)
                val amplitude = size.width * 0.035f
                val phase = sway?.value ?: 0f

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, surface)
                    // Enough segments that the crest is smooth at this size;
                    // this runs in the draw phase, so it is a path rebuild per
                    // frame and not a recomposition.
                    val steps = 18
                    for (i in 0..steps) {
                        val x = size.width * i / steps
                        val y = surface + kotlin.math.sin(
                            phase + i / steps.toFloat() * 2f * Math.PI.toFloat() * 1.5f
                        ) * amplitude
                        lineTo(x, y)
                    }
                    lineTo(size.width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(path, color = tint.copy(alpha = 0.62f))
            }
    )
}
