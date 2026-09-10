package com.leo.imessage.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion
import dev.chrisbanes.haze.HazeState

/**
 * The conversation title bar.
 *
 * Structurally this is unusual and worth getting exactly right: the avatar
 * sits on top, the name hangs *below* it inside a translucent capsule with a
 * trailing chevron, and the back/FaceTime controls are circular glass buttons
 * rather than plain icons. Laying the name out inline next to a back button -
 * the obvious approach - reads as a generic chat app immediately.
 */
@Composable
fun ConversationNavBar(
    chat: Chat,
    hazeState: HazeState,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    darkBase: Boolean? = null,
) {
    val palette = LocalPalette.current

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        hazeState = hazeState,
        tintAlpha = 0.42f,
        blurRadius = 40,
        darkBase = darkBase,
    ) {
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        ) {
            CircleGlassButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Chevron(
                    color = palette.accent,
                    pointingLeft = true,
                    modifier = Modifier.size(17.dp),
                )
            }

            // Avatar over name-pill, centered.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .pointerInput(chat.id) {
                        detectTapGestures { onOpenDetails() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (chat.isGroup) {
                    GroupAvatar(chat.participants, 42.dp)
                } else {
                    Avatar(chat.participants.first(), 42.dp)
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(palette.fieldBackground.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = chat.displayName.substringBefore(' '),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Chevron(
                        color = palette.tertiaryLabel,
                        pointingLeft = false,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }

            CircleGlassButton(
                onClick = {},
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                VideoIcon(color = palette.accent, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun CircleGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.9f, label = "navButtonPress")
    Box(
        modifier
            .size(34.dp)
            .scaleFrom(scale)
            .clip(CircleShape)
            .background(palette.fieldBackground.copy(alpha = 0.8f))
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
        content = { content() },
    )
}

@Composable
fun Chevron(
    color: Color,
    pointingLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            if (pointingLeft) {
                moveTo(w * 0.68f, h * 0.12f)
                lineTo(w * 0.26f, h * 0.5f)
                lineTo(w * 0.68f, h * 0.88f)
            } else {
                moveTo(w * 0.34f, h * 0.12f)
                lineTo(w * 0.76f, h * 0.5f)
                lineTo(w * 0.34f, h * 0.88f)
            }
        }
        drawPath(
            path,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.17f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun VideoIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.10f)
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.24f),
            size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f),
            style = stroke,
        )
        val lens = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.72f, h * 0.42f)
            lineTo(w * 0.95f, h * 0.28f)
            lineTo(w * 0.95f, h * 0.72f)
            lineTo(w * 0.72f, h * 0.58f)
            close()
        }
        drawPath(lens, color)
    }
}
