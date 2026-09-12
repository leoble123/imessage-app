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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
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
import kotlin.math.sin

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
 * screen. So the circle is the display: it fills with colour to the level of
 * what is waiting in it, and its ring breathes while they are typing.
 * Nothing moves unless something is actually happening, which is the only
 * way a moving thing stays informative.
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
 * The ring around a pinned avatar: they are typing, right now.
 *
 * One thing, and only one thing. It used to also draw an arc for unread,
 * which was right while unread was a dot - now that it fills the circle, a
 * count, an arc and a level all saying "eleven" is two of them too many. A
 * signal you have to decode is not a signal.
 */
@Composable
private fun LiveRing(chat: Chat, diameter: Dp) {
    val settings = LocalSettings.current
    val colors = chatRingColorsFor(chat.avatarSeed)
    if (!chat.isTyping) return

    val pulse = if (settings.lowPowerAnimations) {
        // Held at the top of the breath, so a frozen ring still reads as lit
        // rather than as half-drawn.
        1f
    } else {
        rememberInfiniteTransition(label = "ring").animateFloat(
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
    }

    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                val stroke = 2.4.dp.toPx() + 1.4.dp.toPx() * pulse
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = colors + colors.first(),
                        center = center,
                    ),
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(width = stroke),
                    alpha = 0.55f + 0.45f * pulse,
                )
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
 * What makes it read as liquid rather than as a green rectangle is entirely
 * in the surface. The first version of this was one sine wave under one flat
 * fill, and a single sine at this size is a straight line: the whole thing
 * came out as a coloured wash over the avatar with a hard edge across it.
 * Three things fix that, and they are the three things an actual surface of
 * water has:
 *
 *  - **Two waves, not one.** Different frequencies, different speeds. Summed,
 *    they never repeat inside the width of the circle, so the crest travels
 *    instead of the whole line sliding.
 *  - **Depth.** The body is graded from nearly clear at the surface to its
 *    full colour at the bottom, because that is what a tinted volume does.
 *    Flat colour is what makes something look printed on.
 *  - **A meniscus.** A bright line riding exactly on the surface. This is the
 *    single detail doing most of the work - it is what tells the eye there is
 *    a boundary between two materials rather than an edge where a shape was
 *    cropped.
 */
@Composable
private fun UnreadLevel(chat: Chat, diameter: Dp, tint: Color) {
    if (chat.unreadCount <= 0) return
    val settings = LocalSettings.current
    val level = (chat.unreadCount / 10f).coerceIn(0.2f, 0.94f)

    // Settles into place rather than appearing at it, which is most of why it
    // reads as something poured in.
    val filled = remember { Animatable(0f) }
    LaunchedEffect(level) { filled.animateTo(level, com.leo.imessage.ui.theme.Motion.gentle()) }

    val phase = if (settings.lowPowerAnimations) {
        null
    } else {
        rememberInfiniteTransition(label = "unreadLevel").animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
            label = "unreadSway",
        )
    }

    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .drawBehind {
                val w = size.width
                val h = size.height
                val t = phase?.value ?: 0f
                val rest = h * (1f - filled.value)
                val amplitude = (w * 0.045f).coerceAtMost(rest.coerceAtLeast(0f))

                // The surface, sampled once and used for both the body and
                // the line on top of it, so the two can never disagree.
                fun surfaceAt(x: Float): Float {
                    val u = x / w
                    return rest +
                        sin(t + u * TWO_PI * 1.15f) * amplitude +
                        sin(-t * 0.63f + u * TWO_PI * 2.30f) * amplitude * 0.42f
                }

                val steps = 28
                val body = Path().apply {
                    moveTo(0f, surfaceAt(0f))
                    for (i in 1..steps) {
                        val x = w * i / steps
                        lineTo(x, surfaceAt(x))
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }

                // Nearly clear where it meets the air, full colour at depth.
                drawPath(
                    path = body,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.26f),
                            tint.copy(alpha = 0.50f),
                            tint.copy(alpha = 0.66f),
                        ),
                        startY = rest - amplitude,
                        endY = h,
                    ),
                )

                // The meniscus. Drawn as its own stroke rather than as the
                // edge of the fill, because a fill's edge is exactly as sharp
                // as the shape and a surface is not.
                val line = Path().apply {
                    moveTo(0f, surfaceAt(0f))
                    for (i in 1..steps) {
                        val x = w * i / steps
                        lineTo(x, surfaceAt(x))
                    }
                }
                // Denser band first, glint on top of it. The other way round
                // paints the thick line over the thin one and the highlight
                // is simply not there.
                drawPath(
                    path = line,
                    color = tint.copy(alpha = 0.85f),
                    style = Stroke(width = 2.6.dp.toPx()),
                )
                drawPath(
                    path = line,
                    color = Color.White.copy(alpha = 0.5f),
                    style = Stroke(width = 1.1.dp.toPx()),
                )
            }
    )
}

private const val TWO_PI = 6.2831855f
