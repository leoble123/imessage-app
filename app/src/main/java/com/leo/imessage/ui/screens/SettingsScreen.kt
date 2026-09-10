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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<EditableSetting?>(null) }

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 40.dp),
        ) {
            ListSection(header = "Account") {
                SettingsRow(
                    "Apple Account",
                    value = settings.appleAccount.ifBlank { "Not signed in" },
                    onClick = { editing = EditableSetting.APPLE_ACCOUNT },
                )
                SettingsDivider()
                SettingsRow(
                    "Relay Server",
                    value = settings.relayServer.ifBlank { "Not set" },
                    onClick = { editing = EditableSetting.RELAY_SERVER },
                )
                SettingsDivider()
                SettingsRow(
                    "Phone Number",
                    value = settings.phoneNumber.ifBlank { "Not linked" },
                    onClick = { editing = EditableSetting.PHONE_NUMBER },
                )
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
                SettingsRow(
                    "Notification Sound",
                    value = "System",
                    onClick = { openNotificationSettings(context) },
                )
                SettingsDivider()
                SettingsRow(
                    "Focus Filters",
                    value = "System",
                    onClick = { openNotificationSettings(context) },
                )
            }

            ListSection(
                header = "About",
                footer = "Built on the rustpush protocol core. Not affiliated with Apple.",
            ) {
                SettingsRow("Version", value = "0.1.0", showChevron = false)
                SettingsDivider()
                SettingsRow("Backend", value = "Mock", showChevron = false)
                SettingsDivider()
                SettingsRow(
                    "Export Diagnostics",
                    onClick = { shareDiagnostics(context, settings) },
                )
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

        editing?.let { field ->
            SettingEditor(
                field = field,
                initial = when (field) {
                    EditableSetting.APPLE_ACCOUNT -> settings.appleAccount
                    EditableSetting.RELAY_SERVER -> settings.relayServer
                    EditableSetting.PHONE_NUMBER -> settings.phoneNumber
                },
                onCommit = { value ->
                    when (field) {
                        EditableSetting.APPLE_ACCOUNT -> settings.appleAccount = value
                        EditableSetting.RELAY_SERVER -> settings.relayServer = value
                        EditableSetting.PHONE_NUMBER -> settings.phoneNumber = value
                    }
                },
                onDismiss = { editing = null },
            )
        }
    }
}

/** The account fields that open a text editor when tapped. */
enum class EditableSetting(val title: String, val hint: String) {
    APPLE_ACCOUNT("Apple Account", "you@icloud.com"),
    RELAY_SERVER("Relay Server", "http://host:5005"),
    PHONE_NUMBER("Phone Number", "+15555550123"),
}

/**
 * A small editor for the account fields.
 *
 * These have nothing behind them until the rustpush core lands, but a row
 * that opens nothing at all is worse than one that stores what you type -
 * this way the values are real, and there's somewhere for the backend to
 * read them from when it arrives.
 */
@Composable
private fun SettingEditor(
    field: EditableSetting,
    initial: String,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var value by remember(field) { mutableStateOf(initial) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.activity.compose.BackHandler { onDismiss() }
    LaunchedEffect(field) {
        androidx.compose.runtime.withFrameNanos {}
        runCatching { focus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        com.leo.imessage.ui.components.GlassSheet(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(horizontal = 30.dp),
            tintAlpha = 0.68f,
        ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                field.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.label,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.fieldBackground)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        field.hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.tertiaryLabel,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.secondaryLabel,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Save",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.accent,
                    modifier = Modifier
                        .clickable {
                            onCommit(value.trim())
                            onDismiss()
                        }
                        .padding(8.dp),
                )
            }
        }
        }
    }
}

/** Hands notification behaviour to the system, which actually owns it. */
private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** Shares the current configuration as text, for when something misbehaves. */
private fun shareDiagnostics(
    context: android.content.Context,
    settings: com.leo.imessage.ui.theme.AppSettings,
) {
    val report = buildString {
        appendLine("Messages diagnostics")
        appendLine("App version: 0.1.0")
        appendLine("Backend: Mock")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Theme: ${settings.themeMode}")
        appendLine("Bubble style: ${settings.bubbleStyle}")
        appendLine("Reduce motion: ${settings.lowPowerAnimations}")
        appendLine("Relay server: ${settings.relayServer.ifBlank { "not set" }}")
    }
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Messages diagnostics")
            putExtra(android.content.Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Export Diagnostics").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
