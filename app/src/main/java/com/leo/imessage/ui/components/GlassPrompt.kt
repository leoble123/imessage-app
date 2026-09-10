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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState

/** A short text entry on glass - private notes, and anything else one-line. */
@Composable
fun GlassPrompt(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String = "Save",
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }

    BackHandler { onDismiss() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )
        GlassSheet(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                },
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.8f,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.fieldBackground)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.tertiaryLabel,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PromptAction("Cancel", palette.secondaryLabel) { onDismiss() }
                    Spacer(Modifier.width(8.dp))
                    PromptAction(confirmLabel, palette.accent) {
                        onConfirm(value.trim())
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** A short list of choices on glass - reminder times, send-later times. */
@Composable
fun GlassChoice(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    hazeState: HazeState? = null,
    darkBase: Boolean? = null,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return

    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = darkBase,
            onDismiss = onDismiss,
        )
        GlassSheet(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(horizontal = 34.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                },
            hazeState = hazeState,
            darkBase = darkBase,
            tintAlpha = 0.78f,
        ) {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.secondaryLabel,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                )
                options.forEach { (label, action) ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(palette.separator)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                action()
                                onDismiss()
                            }
                            .padding(vertical = 15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
