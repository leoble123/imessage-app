package com.leo.imessage.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.Calls
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.pressScale
import com.leo.imessage.ui.components.scaleFrom
import kotlinx.coroutines.delay

/**
 * The full-screen call UI.
 *
 * Deliberately its own screen rather than a sheet over the conversation: a
 * ringing phone is the most interruptive thing the app does, and it should be
 * unmissable and impossible to dismiss by accident.
 */
@Composable
fun CallScreen(
    call: Calls.Call,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onDismiss: () -> Unit,
    onMuteChange: (Boolean) -> Unit = {},
) {
    var muted by remember { mutableStateOf(false) }

    // An ended call lingers just long enough to read why, then clears itself.
    LaunchedEffect(call.stage) {
        if (call.stage == Calls.Stage.ENDED) {
            delay(1600)
            onDismiss()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Deep, slightly blue-black, the way iOS renders a call over
                // whatever was behind it.
                Brush.verticalGradient(
                    listOf(Color(0xFF1C1C2E), Color(0xFF0A0A14))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))

            call.members.firstOrNull()?.let { person ->
                // The avatar breathes while ringing, so a silent phone still
                // reads as "waiting" rather than frozen.
                val ringing = call.stage == Calls.Stage.INCOMING ||
                    call.stage == Calls.Stage.OUTGOING
                val pulse = rememberInfiniteTransition(label = "ring")
                val scale by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = if (ringing) 1.05f else 1f,
                    animationSpec = infiniteRepeatable(
                        tween(1100), RepeatMode.Reverse,
                    ),
                    label = "pulse",
                )
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                    Avatar(person, 116.dp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                call.title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                statusText(call),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 17.sp,
            )

            Spacer(Modifier.weight(1f))

            if (call.stage == Calls.Stage.ACTIVE) {
                // Only mute, because only mute has something behind it. There
                // is no camera path yet, and a video button that does nothing
                // makes a call look more capable than it is.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CallToggle(
                        on = muted,
                        onIcon = Icons.Filled.MicOff,
                        offIcon = Icons.Filled.Mic,
                        label = if (muted) "Unmute" else "Mute",
                    ) {
                        muted = !muted
                        onMuteChange(muted)
                    }
                }
                Spacer(Modifier.height(36.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp),
                horizontalArrangement = if (call.stage == Calls.Stage.INCOMING) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.Center
                },
            ) {
                when (call.stage) {
                    Calls.Stage.INCOMING -> {
                        RoundButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "Decline", onDecline)
                        RoundButton(Color(0xFF34C759), Icons.Filled.Call, "Accept", onAnswer)
                    }
                    Calls.Stage.OUTGOING, Calls.Stage.ACTIVE -> {
                        RoundButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "End", onHangUp)
                    }
                    Calls.Stage.ENDED -> {}
                }
            }
        }
    }
}

@Composable
private fun statusText(call: Calls.Call): String = when (call.stage) {
    Calls.Stage.INCOMING -> if (call.isVideo) "Incoming FaceTime video" else "Incoming FaceTime"
    Calls.Stage.OUTGOING -> "Calling…"
    Calls.Stage.ENDED -> call.endedReason ?: "Call ended"
    Calls.Stage.ACTIVE -> {
        // Ticks once a second without recomposing anything above it.
        var elapsed by remember { mutableStateOf(0L) }
        LaunchedEffect(call.connectedAt) {
            while (true) {
                elapsed = call.connectedAt?.let { System.currentTimeMillis() - it } ?: 0
                delay(1000)
            }
        }
        val seconds = (elapsed / 1000).coerceAtLeast(0)
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

@Composable
private fun RoundButton(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed, pressedScale = 0.90f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .scaleFrom(scale)
                .clip(CircleShape)
                .background(color)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
private fun CallToggle(
    on: Boolean,
    onIcon: androidx.compose.ui.graphics.vector.ImageVector,
    offIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed, pressedScale = 0.92f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .scaleFrom(scale)
                .clip(CircleShape)
                .background(
                    if (on) Color.White.copy(alpha = 0.92f)
                    else Color.White.copy(alpha = 0.16f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (on) onIcon else offIcon,
                label,
                tint = if (on) Color(0xFF1C1C2E) else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}
