package com.leo.imessage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.components.ChangeList
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.glassSource
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.util.Changelog
import com.leo.imessage.util.Release
import dev.chrisbanes.haze.HazeState

/**
 * Every version this app has been.
 *
 * Worth a screen of its own rather than a link out to a repository: the point
 * is to be able to see where the thing is going while holding it, without a
 * browser and without knowing what a commit is.
 */
@Composable
fun ReleaseNotesScreen(
    onBack: () -> Unit,
    /** The build being run, marked so it's obvious where you are in the list. */
    currentVersionCode: Int = com.leo.imessage.BuildConfig.VERSION_CODE,
) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(hazeState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 100.dp,
                bottom = 48.dp,
            ),
        ) {
            items(Changelog.releases, key = { it.versionCode }) { release ->
                ReleaseCard(release, isCurrent = release.versionCode == currentVersionCode)
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            hazeState = hazeState,
            hairlineAtBottom = true,
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clickable { onBack() }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = palette.accent,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.accent,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = "Release Notes",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.label,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, isCurrent: Boolean) {
    val palette = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                release.versionName,
                style = MaterialTheme.typography.titleMedium,
                color = palette.label,
            )
            if (isCurrent) {
                Spacer(Modifier.padding(horizontal = 4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Installed",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                release.date,
                style = MaterialTheme.typography.labelSmall,
                color = palette.tertiaryLabel,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            release.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.secondaryLabel,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceElevated)
                .padding(16.dp),
        ) {
            ChangeList(release.changes)
        }
    }
}
