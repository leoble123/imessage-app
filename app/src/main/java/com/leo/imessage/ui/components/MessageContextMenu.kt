package com.leo.imessage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.leo.imessage.data.TapbackKind
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

data class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The long-press menu from Messages: everything else dims and recedes, the
 * pressed bubble stays lit and scales up slightly, and a tapback rail plus an
 * action list animate in around it.
 *
 * The scale-up on the focused item is what makes this feel like iOS rather
 * than a dropdown - the bubble is the anchor the whole menu hangs off.
 */
@Composable
fun MessageContextMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTapback: (TapbackKind) -> Unit,
    onEmojiTapback: (String) -> Unit,
    actions: List<MenuAction>,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    darkBase: Boolean? = null,
    focusedContent: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(visible) ?: return

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    // Lifts toward you as it arrives, and sinks back on the
                    // way out - the menu never simply blinks off.
                    val s = 0.9f + 0.1f * p
                    scaleX = s
                    scaleY = s
                    translationY = 20.dp.toPx() * (1f - p)
                },
        ) {
            TapbackRail(
                onPickClassic = onTapback,
                onPickEmoji = onEmojiTapback,
            )

            Spacer(Modifier.height(12.dp))

            focusedContent()

            Spacer(Modifier.height(12.dp))

            GlassSheet(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(252.dp),
                hazeState = hazeState,
                darkBase = darkBase,
                tintAlpha = 0.62f,
            ) {
                Column {
                    actions.forEachIndexed { i, action ->
                        MenuActionRow(action) {
                            action.onClick()
                            onDismiss()
                        }
                        if (i != actions.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
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

@Composable
private fun MenuActionRow(action: MenuAction, onClick: () -> Unit) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale = pressScale(pressed, pressedScale = 0.97f)

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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.destructive) palette.destructive else palette.label,
        )
    }
}
