package com.leo.imessage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.DeliveryState
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.data.Message
import com.leo.imessage.data.MessageRow
import com.leo.imessage.data.Service
import com.leo.imessage.data.TapbackKind
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/** Messages already rendered once, so entry animations only play for new ones. */
private val seenMessages = mutableSetOf<String>()

@Composable
fun MessageBubble(
    row: MessageRow,
    senderName: String?,
    sender: com.leo.imessage.data.Contact? = null,
    /** Set when the thread has a custom background, so bubbles go translucent. */
    onCustomBackground: Boolean = false,
    /** Whether that background is dark, so the glass and text adapt to it. */
    backgroundIsDark: Boolean = false,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 0..1 - how far the swipe-for-timestamps gesture has been dragged.
     *
     * Deliberately a lambda rather than a Float: as a value, every frame of
     * the drag invalidates the parameter and recomposes every bubble on
     * screen. Read inside a graphicsLayer block instead, it only re-runs the
     * draw phase, which is what lets the gesture actually track at panel
     * rate.
     */
    timestampReveal: () -> Float = { 0f },
    /**
     * Replies to this message, shown as a chain link beneath it. Only set
     * once there are two or more - a single reply stays in the transcript
     * with a quote above it instead.
     */
    replyCount: Int = 0,
    /** Set on a lone reply, to render the dimmed original above it. */
    replyParent: Message? = null,
    replyParentSender: String? = null,
    /** Tapping a photo, video or file opens it full screen. */
    onOpenAttachment: (com.leo.imessage.data.Attachment) -> Unit = {},
    onOpenThread: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val msg = row.message
    val outgoing = msg.isFromMe
    val haptics = LocalHapticFeedback.current

    // Entry animation: a newly-arrived bubble springs up from the composer.
    // Messages already on screen when the thread opened must not animate, or
    // every scroll turns into a parade of bubbles flying in.
    val needsGutter = row.showAvatar || row.showSenderName
    val isNew = remember(msg.id) { msg.id !in seenMessages }
    val entry = remember(msg.id) { androidx.compose.animation.core.Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(msg.id) {
        if (isNew) {
            seenMessages += msg.id
            if (settings.lowPowerAnimations) entry.snapTo(1f)
            else entry.animateTo(1f, Motion.bouncy())
        }
    }

    var pressed by remember { mutableStateOf(false) }
    // Held as State and read inside graphicsLayer rather than unwrapped with
    // `by` here: unwrapping makes the whole bubble recompose on every frame
    // of the spring, which is what made the long-press feel like it was
    // stepping rather than gliding.
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = Motion.pressIn(),
        label = "bubblePress",
    )

    Box(modifier.fillMaxWidth()) {
      // The stamp sits under the bubble at the trailing edge and is uncovered
      // as the thread slides left, exactly like Messages.
      Text(
          text = com.leo.imessage.util.messageStamp(msg.timestamp),
          style = MaterialTheme.typography.labelSmall,
          color = palette.tertiaryLabel,
          modifier = Modifier
              .align(Alignment.CenterEnd)
              .graphicsLayer {
                  val r = timestampReveal()
                  translationX = 64.dp.toPx() * (1f - r)
                  alpha = r
              },
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
      ) {
        // Avatar gutter for group threads: reserved even when empty, so a run
        // of bubbles stays aligned instead of stepping in and out.
        if (row.showSenderName || row.showAvatar || (sender != null && !outgoing && needsGutter)) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.BottomCenter) {
                if (row.showAvatar && sender != null) Avatar(sender, 28.dp)
            }
            Spacer(Modifier.width(6.dp))
        }
      Column(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer {
                translationX = -64.dp.toPx() * timestampReveal()
                // Springs up and scales out from the composer's corner.
                translationY = 26.dp.toPx() * (1f - entry.value)
                val s = 0.62f + 0.38f * entry.value
                scaleX = s
                scaleY = s
                alpha = entry.value.coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (outgoing) 1f else 0f,
                    1f,
                )
            },
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (replyParent != null) {
            ReplyQuote(
                parent = replyParent,
                senderName = replyParentSender,
                outgoing = outgoing,
                onOpenThread = onOpenThread,
            )
        }

        if (row.showSenderName && senderName != null && !outgoing) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryLabel,
                modifier = Modifier.padding(start = 14.dp, bottom = 3.dp),
            )
        }

        Box(
            contentAlignment = if (outgoing) Alignment.TopEnd else Alignment.TopStart,
        ) {
            val shape = BubbleShape(outgoing, row.groupPosition)
            val style = settings.bubbleStyle
            val glass = style == com.leo.imessage.ui.theme.BubbleStyle.GLASS
            val overBackground = glass && onCustomBackground
            val transmission = when {
                !glass -> 1f
                overBackground && backgroundIsDark -> GlassAlpha.OUTGOING_ON_DARK_BACKGROUND
                overBackground -> GlassAlpha.OUTGOING_ON_LIGHT_BACKGROUND
                else -> GlassAlpha.OUTGOING
            }
            val fill: Brush = when {
                !outgoing -> if (glass) {
                    incomingGlassFill(
                        grey = palette.incomingBubble,
                        overBackground = overBackground,
                        backgroundIsDark = backgroundIsDark,
                    )
                } else {
                    glassFill(palette.incomingBubble, 1f)
                }
                style == com.leo.imessage.ui.theme.BubbleStyle.FLAT ->
                    glassFill(
                        if (msg.service == Service.SMS) palette.smsBubbleFlat
                        else palette.outgoingBubbleFlat,
                        transmission,
                    )
                else -> glassFill(
                    if (msg.service == Service.SMS) palette.smsBubbleColors
                    else palette.outgoingBubbleColors,
                    transmission,
                )
            }
            // Over a chosen background the incoming bubble is a clear pane,
            // so its text has to follow the background rather than the app's
            // theme or it can end up black on black.
            val incomingTextColor = when {
                !overBackground -> palette.incomingText
                backgroundIsDark -> Color.White
                else -> Color.Black
            }
            val playedEffect = if (settings.playEffects) msg.effect
                else com.leo.imessage.data.MessageEffect.NONE
            val glassDark = when {
                outgoing -> true
                overBackground -> backgroundIsDark
                else -> palette.isDark
            }

            if (msg.isUnsent) {
                UnsentBubble(msg)
            } else {
                Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                    // Attachments stack above any caption, the way a photo
                    // with a message under it arrives on iOS.
                    msg.attachments.forEach { att ->
                        when (att.kind) {
                            com.leo.imessage.data.MediaKind.IMAGE,
                            com.leo.imessage.data.MediaKind.VIDEO -> PhotoAttachment(
                                attachment = att,
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .bubbleEffect(playedEffect, msg.id + att.id),
                                onOpen = { onOpenAttachment(att) },
                                onLongPress = onLongPress,
                            )

                            com.leo.imessage.data.MediaKind.AUDIO -> Box(
                                Modifier
                                    .padding(bottom = 3.dp)
                                    .liquidGlass(shape, fill, glassDark, glass)
                                    .pointerInput(msg.id) {
                                        detectTapGestures(
                                            onLongPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onLongPress()
                                            },
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                AudioAttachment(att, outgoing)
                            }

                            com.leo.imessage.data.MediaKind.FILE -> FileAttachment(
                                attachment = att,
                                modifier = Modifier.padding(bottom = 3.dp),
                                onLongPress = onLongPress,
                            )
                        }
                    }

                    if (msg.text.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .bubbleEffect(playedEffect, msg.id)
                                .graphicsLayer {
                                    val s = pressScale.value
                                    scaleX = s
                                    scaleY = s
                                }
                                .widthIn(max = 290.dp)
                                .liquidGlass(
                                    shape = shape,
                                    fill = fill,
                                    dark = glassDark,
                                    enabled = glass,
                                )
                                .pointerInput(msg.id) {
                                    detectTapGestures(
                                        onPress = {
                                            pressed = true
                                            tryAwaitRelease()
                                            pressed = false
                                        },
                                        onLongPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLongPress()
                                        },
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (outgoing) palette.outgoingText else incomingTextColor,
                            )
                        }
                    }
                }
            }

            if (msg.tapbacks.isNotEmpty()) {
                TapbackCluster(
                    tapbacks = msg.tapbacks,
                    outgoing = outgoing,
                    modifier = Modifier
                        .align(if (outgoing) Alignment.TopStart else Alignment.TopEnd)
                        .offset(
                            x = if (outgoing) (-10).dp else 10.dp,
                            y = (-14).dp,
                        ),
                )
            }
        }

        if (replyCount > 0) {
            ReplyChainLink(
                count = replyCount,
                outgoing = outgoing,
                onClick = onOpenThread,
            )
        }

        if (msg.editedAt != null) {
            Text(
                text = "Edited",
                style = MaterialTheme.typography.labelSmall,
                color = palette.tertiaryLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        if (row.showDeliveryReceipt && outgoing) {
            DeliveryReceipt(msg)
        }
      }
      }
    }
}

/**
 * An unsent message keeps its original text off to the side so it can be
 * revealed in one tap - rather than being genuinely gone, which is what the
 * stock UI implies.
 */
@Composable
private fun UnsentBubble(msg: Message) {
    val palette = LocalPalette.current
    var revealed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(horizontalAlignment = if (msg.isFromMe) Alignment.End else Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, palette.separator, RoundedCornerShape(14.dp))
                .pointerInput(msg.id) {
                    detectTapGestures {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        revealed = !revealed
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = if (revealed) "Hide unsent message" else "Message unsent",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
            )
            Text(
                text = if (revealed) "  ⌃" else "  ⌄",
                style = MaterialTheme.typography.bodySmall,
                color = palette.tertiaryLabel,
            )
        }

        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(Motion.fade()) + expandVertically(Motion.standard()),
            exit = fadeOut(Motion.fade(200)) + shrinkVertically(Motion.snappy()),
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.incomingBubble.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = msg.unsentText.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.secondaryLabel,
                )
            }
        }
    }
}

@Composable
private fun DeliveryReceipt(msg: Message) {
    val palette = LocalPalette.current
    val label = when (msg.deliveryState) {
        DeliveryState.SENDING -> "Sending…"
        DeliveryState.SENT -> "Sent"
        DeliveryState.DELIVERED -> "Delivered"
        DeliveryState.READ -> "Read"
        DeliveryState.FAILED -> "Not Delivered"
    }
    val color = if (msg.deliveryState == DeliveryState.FAILED) {
        palette.destructive
    } else {
        palette.tertiaryLabel
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (msg.deliveryState == DeliveryState.READ) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        modifier = Modifier.padding(top = 3.dp, end = 4.dp, start = 4.dp),
    )
}

@Composable
private fun TapbackCluster(
    tapbacks: List<com.leo.imessage.data.Tapback>,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
    ) {
        tapbacks.take(3).forEach { tb ->
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (outgoing) palette.incomingBubble else palette.outgoingBubbleFlat)
                    .border(1.5.dp, palette.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TapbackIcon(
                    kind = tb.kind,
                    emoji = tb.emoji,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

fun TapbackKind.glyph(): String = when (this) {
    TapbackKind.HEART -> "♥"
    TapbackKind.THUMBS_UP -> "👍"
    TapbackKind.THUMBS_DOWN -> "👎"
    TapbackKind.HAHA -> "HA"
    TapbackKind.EXCLAIM -> "‼"
    TapbackKind.QUESTION -> "?"
    TapbackKind.ANY_EMOJI -> "🙂"
}
