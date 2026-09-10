package com.leo.imessage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState

/**
 * Find-in-conversation.
 *
 * Scrolling for a message you half-remember is the single most common thing
 * a long thread makes you do, and the inbox search only ever answered "which
 * chat" - never "where in it". This answers the second question: matches are
 * counted, stepped through, and the hit is highlighted in place with the
 * messages around it still visible, because a result without its context
 * tells you what was said but not why it mattered.
 */
@Composable
fun ConversationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatch: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    hazeState: HazeState?,
    darkBase: Boolean?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos {}
        runCatching { focus.requestFocus() }
    }

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSheet(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f),
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.6f,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search this conversation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.tertiaryLabel,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }

                if (query.isNotEmpty()) {
                    Text(
                        text = if (matchCount == 0) "None" else "${currentMatch + 1}/$matchCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryLabel,
                    )
                    Spacer(Modifier.width(8.dp))
                    StepButton("↑", enabled = matchCount > 0, onClick = onPrevious)
                    StepButton("↓", enabled = matchCount > 0, onClick = onNext)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            "Done",
            style = MaterialTheme.typography.titleSmall,
            color = palette.accent,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() }
                .padding(6.dp),
        )
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleSmall, color = palette.accent)
    }
}
