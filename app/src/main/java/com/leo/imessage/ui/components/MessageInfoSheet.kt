package com.leo.imessage.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.data.DeliveryState
import com.leo.imessage.data.Message
import com.leo.imessage.data.Service
import com.leo.imessage.media.MediaTools
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import com.leo.imessage.util.messageStamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the app knows about one message.
 *
 * Mostly this exists for the edit history: an edited message shows "Edited"
 * and nothing else, which tells you something changed but not what, and the
 * earlier text is already being kept so that a reveal has something to show.
 */
@Composable
fun MessageInfoSheet(
    message: Message,
    chat: Chat,
    onDismiss: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    darkBase: Boolean? = null,
) {
    val palette = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(message.id) { appear.animateTo(1f, Motion.gentle()) }

    BackHandler { onDismiss() }

    val sender = when {
        message.isFromMe -> "You"
        else -> chat.participants.firstOrNull { it.id == message.senderId }?.displayName ?: "Them"
    }
    val fullStamp = remember(message.timestamp) {
        SimpleDateFormat("EEEE d MMMM 'at' h:mm a", Locale.getDefault())
            .format(Date(message.timestamp))
    }

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { appear.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )

        GlassSheet(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = 360.dp.toPx() * (1f - appear.value) },
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.68f,
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(palette.tertiaryLabel)
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Message Info",
                style = MaterialTheme.typography.titleMedium,
                color = palette.label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            InfoRow("From", sender)
            InfoRow("Sent", fullStamp)
            InfoRow(
                "Service",
                if (message.service == Service.SMS) "SMS" else "iMessage",
            )
            if (message.isFromMe) {
                InfoRow(
                    "Status",
                    when (message.deliveryState) {
                        DeliveryState.SENDING -> "Sending"
                        DeliveryState.SENT -> "Sent"
                        DeliveryState.DELIVERED -> "Delivered"
                        DeliveryState.READ -> "Read"
                        DeliveryState.FAILED -> "Not Delivered"
                    },
                )
            }
            if (message.tapbacks.isNotEmpty()) {
                InfoRow("Reactions", "${message.tapbacks.size}")
            }
            message.attachments.forEach { att ->
                InfoRow(
                    MediaTools.iconLabelFor(att.kind),
                    buildString {
                        append(att.fileName)
                        att.sizeBytes?.let { append("  ·  ${MediaTools.formatSize(it)}") }
                    },
                )
            }
            message.editedAt?.let {
                InfoRow("Edited", messageStamp(it))
            }

            if (message.editHistory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Edit history",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.secondaryLabel,
                )
                Spacer(Modifier.height(6.dp))
                message.editHistory.forEach { previous ->
                    Text(
                        text = previous,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.fieldBackground)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.label,
            textAlign = TextAlign.End,
        )
    }
}

/** The floating "jump to newest" button that appears once you scroll up. */
@Composable
fun JumpToLatest(
    visible: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
    darkBase: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val appear = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.fluid(),
        label = "jumpAppear",
    )

    GlassPill(
        modifier = modifier
            .graphicsLayer {
                val a = appear.value
                alpha = a
                scaleX = 0.7f + 0.3f * a
                scaleY = 0.7f + 0.3f * a
                translationY = 16.dp.toPx() * (1f - a)
            }
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible,
            ) { onClick() },
        hazeState = hazeState,
        darkBase = darkBase,
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Chevron(
                color = palette.accent,
                pointingLeft = false,
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer { rotationZ = 90f },
            )
        }
    }
}
