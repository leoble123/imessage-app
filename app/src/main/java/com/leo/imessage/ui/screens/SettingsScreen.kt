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
    val settings = com.leo.imessage.ui.theme.LocalSettings.current

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 40.dp),
        ) {
            ListSection(header = "Account") {
                SettingsRow("Apple Account", value = "Not signed in")
                SettingsDivider()
                SettingsRow("Relay Server", value = "Not set")
                SettingsDivider()
                SettingsRow("Phone Number", value = "Not linked")
            }

            ListSection(header = "Appearance") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = listOf(
                            com.leo.imessage.ui.theme.ThemeMode.SYSTEM to "System",
                            com.leo.imessage.ui.theme.ThemeMode.LIGHT to "Light",
                            com.leo.imessage.ui.theme.ThemeMode.DARK to "Dark",
                        ),
                        selected = settings.themeMode,
                        onSelect = { settings.themeMode = it },
                    )
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Bubble Style",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = listOf(
                            com.leo.imessage.ui.theme.BubbleStyle.GLASS to "Glass",
                            com.leo.imessage.ui.theme.BubbleStyle.GRADIENT to "Gradient",
                            com.leo.imessage.ui.theme.BubbleStyle.FLAT to "Flat",
                        ),
                        selected = settings.bubbleStyle,
                        onSelect = { settings.bubbleStyle = it },
                    )
                }
                SettingsDivider()
                SettingsToggle("Compact Chat List", settings.compactChatList) {
                    settings.compactChatList = it
                }
            }

            ListSection(
                header = "Messaging",
                footer = "Turning off read receipts also hides yours from others.",
            ) {
                SettingsToggle("Send Read Receipts", settings.sendReadReceipts) {
                    settings.sendReadReceipts = it
                }
                SettingsDivider()
                SettingsToggle("Show Typing Indicators", settings.showTypingIndicators) {
                    settings.showTypingIndicators = it
                }
                SettingsDivider()
                SettingsToggle("Send with Return Key", settings.sendWithReturn) {
                    settings.sendWithReturn = it
                }
                SettingsDivider()
                SettingsRow("Blocked Contacts", value = "0")
            }

            ListSection(
                header = "Gestures & Motion",
                footer = "Reduce Motion trims spring animations for a calmer, faster feel.",
            ) {
                SettingsToggle("Swipe to Reply", settings.swipeToReply) {
                    settings.swipeToReply = it
                }
                SettingsDivider()
                SettingsToggle("Swipe for Timestamps", settings.showTimestampsOnSwipe) {
                    settings.showTimestampsOnSwipe = it
                }
                SettingsDivider()
                SettingsToggle("Play Message Effects", settings.playEffects) {
                    settings.playEffects = it
                }
                SettingsDivider()
                SettingsToggle("Haptic Feedback", settings.hapticsEnabled) {
                    settings.hapticsEnabled = it
                }
                SettingsDivider()
                SettingsToggle("Reduce Motion", settings.lowPowerAnimations) {
                    settings.lowPowerAnimations = it
                }
            }

            ListSection(header = "Notifications") {
                SettingsToggle("Unread Badges", settings.showUnreadBadges) {
                    settings.showUnreadBadges = it
                }
                SettingsDivider()
                SettingsRow("Notification Sound", value = "Default")
                SettingsDivider()
                SettingsRow("Focus Filters", value = "Off")
            }

            ListSection(
                header = "About",
                footer = "Built on the rustpush protocol core. Not affiliated with Apple.",
            ) {
                SettingsRow("Version", value = "0.1.0", showChevron = false)
                SettingsDivider()
                SettingsRow("Backend", value = "Mock", showChevron = false)
                SettingsDivider()
                SettingsRow("Export Logs")
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
