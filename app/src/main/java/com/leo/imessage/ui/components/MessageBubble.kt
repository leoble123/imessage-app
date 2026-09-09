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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.DeliveryState
import com.leo.imessage.data.GroupPosition
import com.leo.imessage.data.Message
import com.leo.imessage.data.MessageRow
import com.leo.imessage.data.Service
import com.leo.imessage.data.TapbackKind
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

@Composable
fun MessageBubble(
    row: MessageRow,
    senderName: String?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val msg = row.message
    val outgoing = msg.isFromMe
    val haptics = LocalHapticFeedback.current

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.snappy(),
        label = "bubblePress",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
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
            if (msg.isUnsent) {
                UnsentBubble(msg)
            } else if (msg.attachments.isNotEmpty() && msg.text.isBlank()) {
                Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                    msg.attachments.forEach { att ->
                        if (att.isImage) {
                            PhotoAttachment(
                                att,
                                Modifier
                                    .padding(bottom = 3.dp)
                                    .bubbleEffect(msg.effect, msg.id + att.id),
                            )
                        } else {
                            FileAttachment(att, Modifier.padding(bottom = 3.dp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .bubbleEffect(msg.effect, msg.id)
                        .scale(pressScale)
                        .widthIn(max = 290.dp)
                        .clip(BubbleShape(outgoing, row.groupPosition))
                        .then(
                            if (outgoing) {
                                Modifier.background(
                                    if (msg.service == Service.SMS) palette.smsBubble
                                    else palette.outgoingBubble
                                )
                            } else {
                                Modifier.background(palette.incomingBubble)
                            }
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
                        color = if (outgoing) palette.outgoingText else palette.incomingText,
                    )
                }
            }

            if (msg.tapbacks.isNotEmpty()) {
                TapbackCluster(
                    kinds = msg.tapbacks.map { it.kind },
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
            exit = fadeOut(Motion.fade(120)) + shrinkVertically(Motion.snappy()),
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
    kinds: List<TapbackKind>,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
    ) {
        kinds.take(3).forEach { kind ->
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (outgoing) palette.incomingBubble else palette.outgoingBubbleFlat)
                    .border(1.5.dp, palette.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = kind.glyph(),
                    color = if (outgoing) palette.incomingText else Color.White,
                    style = MaterialTheme.typography.labelMedium,
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
}
