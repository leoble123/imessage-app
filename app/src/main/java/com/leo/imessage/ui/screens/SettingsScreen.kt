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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.components.GlassSurface
import com.leo.imessage.ui.components.glassSource
import dev.chrisbanes.haze.HazeState
import com.leo.imessage.ui.components.ListSection
import com.leo.imessage.ui.components.SettingsDivider
import com.leo.imessage.ui.components.SettingsRow
import com.leo.imessage.ui.components.SettingsToggle
import com.leo.imessage.ui.theme.LocalPalette

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }
    var readReceipts by remember { mutableStateOf(true) }
    var typingIndicators by remember { mutableStateOf(true) }
    var effects by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 32.dp),
        ) {
            ListSection(header = "Account") {
                SettingsRow("Apple Account", value = "Not signed in")
                SettingsDivider()
                SettingsRow("Relay Server", value = "Not set")
            }

            ListSection(
                header = "Messaging",
                footer = "Turning off read receipts also hides yours from others.",
            ) {
                SettingsToggle("Send Read Receipts", readReceipts) { readReceipts = it }
                SettingsDivider()
                SettingsToggle("Show Typing Indicators", typingIndicators) { typingIndicators = it }
                SettingsDivider()
                SettingsToggle("Play Message Effects", effects) { effects = it }
            }

            ListSection(header = "Appearance") {
                SettingsRow("Theme", value = "System")
                SettingsDivider()
                SettingsToggle("Haptic Feedback", haptics) { haptics = it }
            }

            ListSection(
                header = "About",
                footer = "Built on the rustpush protocol core. Not affiliated with Apple.",
            ) {
                SettingsRow("Version", value = "0.1.0", showChevron = false)
                SettingsDivider()
                SettingsRow("Backend", value = "Mock", showChevron = false)
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
                            "Messages",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.accent,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.label,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}
