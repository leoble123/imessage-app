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
    focusedContent: @Composable () -> Unit,
) {
    val palette = LocalPalette.current

    // Both held as State and read inside draw-phase lambdas: unwrapping them
    // here would recompose the whole menu - scrim, rail, bubble and every
    // action row - on every frame of the spring.
    val scrim = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.standard(),
        label = "ctxScrim",
    )
    val pop = animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = Motion.bouncy(),
        label = "ctxPop",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.fade(160)),
        exit = fadeOut(Motion.fade(140)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.42f * scrim.value))
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        val s = pop.value
                        scaleX = s
                        scaleY = s
                    },
            ) {
                TapbackRail(
                    onPickClassic = onTapback,
                    onPickEmoji = onEmojiTapback,
                )

                Spacer(Modifier.height(12.dp))

                focusedContent()

                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier
                        .width(250.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (action.destructive) palette.destructive else palette.label,
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
}

