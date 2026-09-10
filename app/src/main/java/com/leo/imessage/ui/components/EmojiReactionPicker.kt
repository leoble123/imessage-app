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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.imessage.data.TapbackKind
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/** The six classic tapbacks, in the order iOS shows them. */
val ClassicTapbacks = listOf(
    TapbackKind.HEART,
    TapbackKind.THUMBS_UP,
    TapbackKind.THUMBS_DOWN,
    TapbackKind.HAHA,
    TapbackKind.EXCLAIM,
    TapbackKind.QUESTION,
)

/** Emoji offered when expanding past the six classics, as on iOS 18. */
private val EmojiPalette = listOf(
    "😀", "😂", "🥹", "😍", "🤩", "😎", "🥳", "🤔",
    "😅", "😭", "😤", "😱", "🙄", "😴", "🤯", "🫠",
    "👍", "👎", "👏", "🙏", "💪", "🤝", "🫶", "✌️",
    "❤️", "🔥", "💯", "✨", "🎉", "⚡", "💀", "👀",
    "✅", "❌", "⭐", "🚀", "🍕", "☕", "🏀", "🎧",
)

/**
 * The tapback rail: six classics, plus a "+" that expands into a full emoji
 * grid so any emoji can be used as a reaction (iOS 18 behaviour).
 */
@Composable
fun TapbackRail(
    onPickClassic: (TapbackKind) -> Unit,
    onPickEmoji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(palette.surfaceElevated)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClassicTapbacks.forEach { kind ->
                TapbackButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPickClassic(kind)
                }) {
                    TapbackIcon(kind = kind, fontSize = 22.sp)
                }
            }
            TapbackButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = !expanded
            }) {
                Text(
                    text = if (expanded) "\u00d7" else "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.secondaryLabel,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.fade(140)),
            exit = fadeOut(Motion.fade(120)),
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceElevated)
                    .padding(8.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth(),
                ) {
                    items(EmojiPalette) { emoji ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPickEmoji(emoji)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapbackButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.28f else 1f,
        animationSpec = Motion.bouncy(),
        label = "tapbackPress",
    )
    Box(
        Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
