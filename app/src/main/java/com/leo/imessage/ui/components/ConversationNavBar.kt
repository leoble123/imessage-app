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
    onFaceTime: () -> Unit = {},
    onSearch: () -> Unit = {},
    onCatchUp: () -> Unit = {},
    hasUnread: Boolean = false,
    modifier: Modifier = Modifier,
    darkBase: Boolean? = null,
) {
    val palette = LocalPalette.current

    // No bar. Each control is its own piece of glass floating over the
    // thread, which is the point of a real backdrop blur: a full-width slab
    // hides the wallpaper behind an opaque-looking band and throws away the
    // depth the blur was there to create. Floating them lets the
    // conversation and the wallpaper run edge to edge underneath.
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        ) {
            CircleGlassButton(
                onClick = onBack,
                hazeState = hazeState,
                darkBase = darkBase,
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
                        detectTapGestures(
                            onTap = { onOpenDetails() },
                            // Long-press the title to search the thread -
                            // the same spot that opens its details, since
                            // both are questions about "this conversation".
                            onLongPress = { onSearch() },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (chat.isGroup) {
                    GroupAvatar(chat.participants, 42.dp)
                } else {
                    Avatar(chat.participants.first(), 42.dp)
                }
                Spacer(Modifier.height(3.dp))
                GlassPill(
                    modifier = Modifier.clip(RoundedCornerShape(11.dp)),
                    hazeState = hazeState,
                    darkBase = darkBase,
                    shape = RoundedCornerShape(11.dp),
                    // Thicker than the bars. This one floats over the
                    // transcript rather than over a background, and at bar
                    // opacity the message behind it read straight through the
                    // capsule - both the name and the message underneath were
                    // unreadable at once.
                    fill = com.leo.imessage.ui.theme.Materials.thick(
                        darkBase ?: LocalPalette.current.isDark
                    ),
                ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        // First name only for people, whole thing otherwise.
                        // A formatted number is full of spaces, so trimming at
                        // the first one turns "+1 815 555 0123" into "+1".
                        text = chat.displayName.let { name ->
                            if (name.count { it.isDigit() } >= 5) name
                            else name.substringBefore(' ')
                        },
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
            }

            Row(
                Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Only offered when there is a backlog. A permanent button for
                // "catch me up" on a thread you have already read is clutter
                // pretending to be a feature.
                if (hasUnread) {
                    CircleGlassButton(
                        onClick = onCatchUp,
                        hazeState = hazeState,
                        darkBase = darkBase,
                    ) {
                        Text(
                            "\u2728",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                CircleGlassButton(
                    onClick = onFaceTime,
                    hazeState = hazeState,
                    darkBase = darkBase,
                ) {
                    VideoIcon(color = palette.accent, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun CircleGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.9f, label = "navButtonPress")
    GlassPill(
        modifier = modifier
            .size(34.dp)
            .scaleFrom(scale)
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
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { content() }
    }
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
