package com.leo.imessage.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leo.imessage.util.CatchUp
import com.leo.imessage.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState

/** The "what did I miss" panel. */
@Composable
fun CatchUpSheet(
    summary: CatchUp,
    hazeState: HazeState?,
    darkBase: Boolean?,
    onJumpToFirstUnread: () -> Unit,
    onJumpTo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return

    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize()) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )
        GlassSheet(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = 420.dp.toPx() * (1f - progress.value) },
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.72f,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            ) {
                Text(
                    summary.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                if (summary.people.isNotEmpty()) {
                    Text(
                        summary.people.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (summary.mediaCount > 0) {
                        Stat("${summary.mediaCount}", "media", Modifier.weight(1f))
                    }
                    if (summary.links.isNotEmpty()) {
                        Stat("${summary.links.size}", "links", Modifier.weight(1f))
                    }
                    if (summary.questions.isNotEmpty()) {
                        Stat("${summary.questions.size}", "questions", Modifier.weight(1f))
                    }
                }

                if (summary.questions.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Waiting on you",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.secondaryLabel,
                    )
                    summary.questions.take(4).forEach { message ->
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 7.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.fieldBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onJumpTo(message.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "Jump to where I left off",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent.copy(alpha = 0.14f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onJumpToFirstUnread()
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.fieldBackground)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = palette.label)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.secondaryLabel)
    }
}
