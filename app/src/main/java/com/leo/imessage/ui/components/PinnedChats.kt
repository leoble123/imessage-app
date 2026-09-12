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
import kotlin.math.exp
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
 * question you actually have when you glance at a row of faces.
 *
 * The level is deliberately not proportional to the count. Proportional
 * scaling sounds right and is wrong twice over: one unread barely shows, and
 * anything past a dozen drowns the face you are trying to recognise. This
 * rises fast at the start, where the difference between one and three is
 * genuinely interesting, and flattens off toward a ceiling it never reaches,
 * because the gap left at the top is most of what makes it read as a liquid
 * in a container rather than a filled shape.
 *
 * What makes it read as liquid at all is the surface. Two waves at different
 * speeds so the crest travels rather than the line sliding; a body graded
 * from nearly clear at the top to full colour at depth, because that is what
 * a tinted volume does; a meniscus riding the surface, which is the detail
 * that says "boundary between two materials" rather than "shape that got
 * cropped"; and the surface pulled up where it meets the glass, which is
 * what water actually does and is the difference between a liquid and a
 * wavy line.
 *
 * It is nearly still when nothing is happening. Motion here is a signal, and
 * a signal that never stops is decoration.
 */
@Composable
private fun UnreadLevel(chat: Chat, diameter: Dp, tint: Color) {
    val settings = LocalSettings.current
    val target = levelFor(chat.unreadCount)

    val filled = remember { Animatable(0f) }
    // Kept composed at zero rather than removed, so emptying is something you
    // watch drain. Returning early the moment the count hit zero meant the
    // spring below never got to run and the liquid simply vanished.
    if (chat.unreadCount <= 0 && filled.value <= 0.001f) return

    LaunchedEffect(target) {
        filled.animateTo(target, com.leo.imessage.ui.theme.Motion.gentle())
    }

    // A message landing disturbs the surface and settles. This is the only
    // thing that ever makes it move much, which is why it reads as an event.
    val splash = remember { Animatable(0f) }
    var lastCount by remember { mutableStateOf(chat.unreadCount) }
    LaunchedEffect(chat.unreadCount) {
        val arrived = chat.unreadCount > lastCount
        lastCount = chat.unreadCount
        if (!arrived || settings.lowPowerAnimations) return@LaunchedEffect
        splash.snapTo(1f)
        splash.animateTo(0f, tween(1600, easing = LinearEasing))
    }

    val phase = if (settings.lowPowerAnimations) {
        null
    } else {
        rememberInfiniteTransition(label = "unreadLevel").animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
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
                val level = filled.value
                if (level <= 0.001f) return@drawBehind
                val rest = h * (1f - level)

                // Almost flat at rest; briefly alive when something lands.
                val calm = w * 0.012f
                val amplitude = (calm + w * 0.055f * splash.value)
                    .coerceAtMost(rest.coerceAtLeast(0f))

                fun surfaceAt(x: Float): Float {
                    val u = (x / w).coerceIn(0f, 1f)
                    val wave = sin(t + u * TWO_PI * 1.15f) +
                        sin(-t * 0.63f + u * TWO_PI * 2.30f) * 0.42f
                    // Surface tension: held up against the glass on both
                    // sides and free in the middle. Without it the wave runs
                    // straight into the wall and gets guillotined by the clip.
                    val wall = sin(u * kotlin.math.PI.toFloat())
                    val cling = (1f - wall) * w * 0.035f
                    return rest + wave * amplitude * wall - cling
                }

                val steps = 30
                fun trace(path: Path) {
                    path.moveTo(0f, surfaceAt(0f))
                    for (i in 1..steps) {
                        val x = w * i / steps
                        path.lineTo(x, surfaceAt(x))
                    }
                }

                val body = Path().apply {
                    trace(this)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = body,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.24f),
                            tint.copy(alpha = 0.48f),
                            tint.copy(alpha = 0.64f),
                        ),
                        startY = rest - amplitude,
                        endY = h,
                    ),
                )

                // Denser band first, glint on top of it. The other way round
                // paints the thick line over the thin one and the highlight
                // is simply not there.
                val line = Path().apply { trace(this) }
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

/**
 * How full the circle gets for a given number of unread messages.
 *
 * Roughly: 1 is a sixth, 3 is a third, 10 is two thirds, and fifty-odd
 * approaches but never reaches the ceiling. Saturating rather than linear,
 * so the first few messages are the ones that move it most - which is also
 * the order in which they stop being interesting.
 */
internal fun levelFor(unread: Int): Float {
    if (unread <= 0) return 0f
    return (CEILING * (1f - exp(-unread / 7.2f))).coerceAtLeast(0.15f)
}

/** Never quite full: the gap at the top is what makes it a liquid. */
private const val CEILING = 0.88f

private const val TWO_PI = 6.2831855f
