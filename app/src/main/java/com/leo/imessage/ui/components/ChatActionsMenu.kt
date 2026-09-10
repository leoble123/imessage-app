package com.leo.imessage.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.Chat
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/** One row in the long-press menu for a conversation. */
data class ChatAction(
    val label: String,
    val glyph: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press menu on a conversation.
 *
 * Same shape as the message menu: everything dims, the conversation you
 * pressed stays lit and lifted, and the actions hang off it. Pin, Hide
 * Alerts, Mark Unread, Archive and Delete are all here because the swipe
 * actions can only ever hold two or three before they stop being findable.
 */
@Composable
fun ChatActionsMenu(
    chat: Chat,
    actions: List<ChatAction>,
    onDismiss: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    preview: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return

    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = null,
            onDismiss = onDismiss,
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 26.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    val s = 0.9f + 0.1f * p
                    scaleX = s
                    scaleY = s
                    translationY = 18.dp.toPx() * (1f - p)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassSheet(
                shape = RoundedCornerShape(18.dp),
                hazeState = hazeState,
                tintAlpha = 0.7f,
            ) {
                preview()
            }

            Spacer(Modifier.height(14.dp))

            GlassSheet(
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
                tintAlpha = 0.62f,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { i, action ->
                        ActionRow(action) {
                            action.onClick()
                            onDismiss()
                        }
                        if (i != actions.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp)
                                    .height(0.5.dp)
                                    .background(palette.separator)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A menu row that dips under the finger rather than flashing a ripple. */
@Composable
private fun ActionRow(action: ChatAction, onClick: () -> Unit) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.97f)
    val tint = if (action.destructive) palette.destructive else palette.label

    Row(
        Modifier
            .fillMaxWidth()
            .scaleFrom(scale)
                        .pointerInput(action.label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = action.glyph,
            style = MaterialTheme.typography.titleMedium,
            color = tint.copy(alpha = 0.9f),
        )
    }
}
