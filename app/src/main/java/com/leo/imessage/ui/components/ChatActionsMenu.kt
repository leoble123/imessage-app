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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    preview: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(chat.id) { appear.animateTo(1f, Motion.bouncy()) }

    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * appear.value
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceElevated)
            ) {
                preview()
            }

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(palette.surfaceElevated),
            ) {
                actions.forEachIndexed { i, action ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                action.onClick()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.destructive) palette.destructive else palette.label,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = action.glyph,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.destructive) palette.destructive else palette.accent,
                        )
                    }
                    if (i != actions.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(palette.separator)
                        )
                    }
                }
            }
        }
    }
}
