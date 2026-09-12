package com.leo.imessage.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.Calls
import com.leo.imessage.ui.components.Avatar
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.glassSource
import com.leo.imessage.ui.components.pressScale
import com.leo.imessage.ui.components.rememberHaptics
import com.leo.imessage.ui.components.scaleFrom
import com.leo.imessage.ui.theme.chatRingColorsFor
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The full-screen call UI.
 *
 * Deliberately its own screen rather than a sheet over the conversation: a
 * ringing phone is the most interruptive thing the app does, and it should be
 * unmissable and impossible to dismiss by accident.
 *
 * Everything that moves here moves in the draw phase. The backdrop is three
 * drifting blobs and the ring is three expanding circles, all of them reading
 * their animation inside a draw lambda rather than during composition - a
 * call screen that recomposed sixty times a second would be doing it while
 * the codec threads want the CPU.
 */
@Composable
fun CallScreen(
    call: Calls.Call,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onDismiss: () -> Unit,
    onMuteChange: (Boolean) -> Unit = {},
    /**
     * Switches the loudspeaker, and reports what the platform actually did.
     * A route can be refused; the control follows the answer rather than the
     * request, so it never sits there lit over an earpiece.
     */
    onSpeakerChange: (Boolean) -> Boolean = { it },
) {
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    // An ended call lingers just long enough to read why, then clears itself.
    LaunchedEffect(call.stage) {
        if (call.stage == Calls.Stage.ENDED) {
            delay(1600)
            onDismiss()
        }
    }

    val ringing = call.stage == Calls.Stage.INCOMING || call.stage == Calls.Stage.OUTGOING
    val person = call.members.firstOrNull()
    val accents = remember(person?.id) {
        person?.id?.let { chatRingColorsFor(it) } ?: listOf(Color(0xFF3B6FE0), Color(0xFF7A3BE0))
    }

    // The glass controls sample the backdrop, so the backdrop has to be a
    // blur source of its own - and only the backdrop. Sampling the whole
    // screen would put the controls inside their own input.
    val backdrop = remember { HazeState() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(
            accents = accents,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            GlassSurface(
                modifier = Modifier.clip(RoundedCornerShape(50)),
                hazeState = backdrop,
                darkBase = true,
            ) {
                Text(
                    text = if (call.isVideo) "FaceTime Video" else "FaceTime Audio",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.height(44.dp))

            person?.let {
                Box(contentAlignment = Alignment.Center) {
                    if (ringing) PulseRings(accents.first())
                    // Breathing, so a silent phone still reads as "waiting"
                    // rather than frozen.
                    val pulse = rememberInfiniteTransition(label = "ring")
                    val scale = pulse.animateFloat(
                        initialValue = 1f,
                        targetValue = if (ringing) 1.05f else 1f,
                        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                        label = "pulse",
                    )
                    Box(
                        Modifier.graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                    ) {
                        Avatar(it, 116.dp)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

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

            // One capsule of glass rather than loose circles on a background:
            // this is the shape iOS settled on, and it also means the controls
            // share one blur pass instead of one each.
            AnimatedVisibility(
                visible = call.stage == Calls.Stage.ACTIVE,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                GlassSurface(
                    modifier = Modifier.clip(RoundedCornerShape(34.dp)),
                    hazeState = backdrop,
                    darkBase = true,
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CallToggle(
                            on = muted,
                            onIcon = Icons.Filled.MicOff,
                            offIcon = Icons.Filled.Mic,
                            label = if (muted) "Unmute" else "Mute",
                        ) {
                            muted = !muted
                            onMuteChange(muted)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        CallToggle(
                            on = speaker,
                            onIcon = Icons.Filled.VolumeUp,
                            offIcon = Icons.Filled.VolumeOff,
                            label = "Speaker",
                        ) {
                            speaker = onSpeakerChange(!speaker)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 52.dp),
                horizontalArrangement = if (call.stage == Calls.Stage.INCOMING) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.Center
                },
            ) {
                when (call.stage) {
                    Calls.Stage.INCOMING -> {
                        RoundButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "Decline") {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDecline()
                        }
                        RoundButton(Color(0xFF34C759), Icons.Filled.Call, "Accept") {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswer()
                        }
                    }
                    Calls.Stage.OUTGOING, Calls.Stage.ACTIVE -> {
                        RoundButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "End") {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHangUp()
                        }
                    }
                    Calls.Stage.ENDED -> {}
                }
            }
        }
    }
}

/**
 * The moving ground the call sits on.
 *
 * Three wide radial blobs in the caller's own two colours, drifting on
 * circles of different radii at different rates, over a near-black base. They
 * never repeat visibly because the periods don't divide into each other, and
 * they cost one draw call each because a radial gradient is a shader rather
 * than anything this has to rasterise itself.
 */
@Composable
private fun AmbientBackdrop(accents: List<Color>, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "drift")
    // One angle, three speeds off it. Separate animations would drift out of
    // step across process death and configuration changes; one clock can't.
    val t = drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "t",
    )

    val warm = accents.first()
    val cool = accents.getOrElse(1) { accents.first() }

    Canvas(modifier.background(Color(0xFF07070C))) {
        val w = size.width
        val h = size.height
        // Read once, in the draw phase. Reading `t.value` during composition
        // instead would recompose the whole call screen every frame.
        val a = t.value

        fun blob(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
            )
        }

        blob(
            w * (0.28f + 0.13f * cos(a)),
            h * (0.24f + 0.07f * sin(a)),
            w * 0.85f, warm, 0.40f,
        )
        blob(
            w * (0.78f + 0.11f * cos(a * 0.62f + 2.1f)),
            h * (0.40f + 0.09f * sin(a * 0.62f + 2.1f)),
            w * 0.78f, cool, 0.34f,
        )
        blob(
            w * (0.50f + 0.16f * cos(a * 0.37f + 4.2f)),
            h * (0.80f + 0.06f * sin(a * 0.37f + 4.2f)),
            w * 0.95f, warm, 0.20f,
        )
    }
}

/**
 * Three rings leaving the avatar while the phone is ringing.
 *
 * Staggered by a third of the period each, so there is always one mid-flight
 * and the effect reads as continuous rather than as a pulse with gaps in it.
 */
@Composable
private fun PulseRings(color: Color) {
    val rings = rememberInfiniteTransition(label = "rings")
    val p = rings.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "p",
    )
    Canvas(Modifier.size(260.dp)) {
        val base = size.minDimension * 0.225f
        val phase = p.value
        repeat(3) { i ->
            // Each ring is the same animation a third of a cycle apart.
            val f = (phase + i / 3f) % 1f
            val radius = base + (size.minDimension / 2f - base) * f
            drawCircle(
                color = color.copy(alpha = 0.34f * (1f - f) * (1f - f)),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()),
            )
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
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
                tint = if (on) Color(0xFF14141C) else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
