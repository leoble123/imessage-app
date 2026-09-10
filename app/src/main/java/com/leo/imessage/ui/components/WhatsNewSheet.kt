package com.leo.imessage.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.util.Release
import dev.chrisbanes.haze.HazeState

/**
 * What changed in the build you just installed, shown once.
 *
 * "Once" is the whole design. A card that reappears is an interruption; a
 * card that appears exactly at the moment the app changed under you is an
 * explanation. It is dismissed by acknowledging it - there is no scrim tap
 * and no back gesture out of it - because the version is marked as seen on
 * the way out, and a dismissal that skips that step shows it again tomorrow.
 */
@Composable
fun WhatsNewSheet(
    release: Release,
    hazeState: HazeState? = null,
    onSeeAll: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val progress = rememberPanelProgress(true) ?: return
    val haptics = rememberHaptics()

    // Deliberately swallowed rather than left to fall through to whatever is
    // behind: backing out of this card would leave the version unmarked.
    BackHandler { }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassScrim(
            progress = { progress.value },
            hazeState = hazeState,
            darkBase = null,
            onDismiss = {},
        )
        GlassSheet(
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                },
            hazeState = hazeState,
            tintAlpha = 0.82f,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                Text(
                    "What's New",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Relay ${release.versionName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    release.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.secondaryLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))

                // Capped and scrollable. A release with a dozen lines in it
                // would otherwise push the button that dismisses the card off
                // the bottom of the screen, which is the one control here.
                ChangeList(
                    changes = release.changes,
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                )

                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Continue",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                }
                if (onSeeAll != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "See all release notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onDismiss()
                                onSeeAll()
                            }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
