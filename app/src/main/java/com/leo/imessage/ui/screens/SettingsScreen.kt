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
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.leo.imessage.data.AccountSummary
import com.leo.imessage.ui.theme.LocalPalette

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** The account this app is signed into, or null when running on sample data. */
    account: AccountSummary? = null,
    onSignOut: () -> Unit = {},
    /** Runs an OpenBubbles import and reports what it found. */
    onImport: (suspend (android.net.Uri) -> String)? = null,
    /** Opens the full version history. */
    onOpenReleaseNotes: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingTemplates by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(palette.groupedBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 100.dp, bottom = 40.dp),
        ) {
            // These read the live account rather than editable text fields.
            // They used to be free-text settings, which meant you could type a
            // relay address that changed nothing and an Apple ID that wasn't
            // the one you were signed in as.
            ListSection(header = "Account") {
                if (account == null) {
                    SettingsRow("Sample data", value = "No account", showChevron = false)
                } else {
                    SettingsRow(
                        "Sending from",
                        value = account.primaryHandle ?: "Unknown",
                        showChevron = false,
                    )
                    if (account.otherHandles.isNotEmpty()) {
                        SettingsDivider()
                        SettingsRow(
                            "Also reachable at",
                            value = account.otherHandles.joinToString(", "),
                            showChevron = false,
                        )
                    }
                    SettingsDivider()
                    SettingsRow("Server", value = account.relay ?: "Not set", showChevron = false)
                    SettingsDivider()
                    SettingsRow(
                        "Services",
                        value = if (account.servicesComplete) "Registered"
                        else "Incomplete - restart the app",
                        showChevron = false,
                        destructive = !account.servicesComplete,
                    )
                    account.historyProblem?.let {
                        SettingsDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "Saved messages",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.destructive,
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                    }
                    account.lastSend?.let {
                        SettingsDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "Last send",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.label,
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsRow(
                    if (account == null) "Set up an account" else "Sign out",
                    showChevron = false,
                    destructive = account != null,
                    onClick = onSignOut,
                )
            }

            if (onImport != null) {
                val scope = rememberCoroutineScope()
                var status by remember { mutableStateOf<String?>(null) }
                var running by remember { mutableStateOf(false) }
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    running = true
                    status = "Importing…"
                    scope.launch {
                        status = try {
                            onImport(uri)
                        } catch (e: Exception) {
                            e.message ?: "That import didn't work."
                        }
                        running = false
                    }
                }

                ListSection(header = "Messages") {
                    SettingsRow(
                        "Import from OpenBubbles",
                        value = if (running) "Working…" else null,
                        onClick = {
                            if (!running) {
                                // The export has no registered type, so filter
                                // by nothing and let the file name speak.
                                picker.launch(arrayOf("*/*"))
                            }
                        },
                    )
                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryLabel,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, bottom = 12.dp,
                            ),
                        )
                    }
                }
                Text(
                    "In OpenBubbles: Settings → Backup & Restore → Export Messages. " +
                        "Then pick the BlueBubbles-chats file from your Downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.tertiaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp),
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
                            com.leo.imessage.ui.theme.ThemeMode.OLED to "OLED",
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
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Accent Color",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 11.dp),
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(com.leo.imessage.ui.theme.AccentColor.entries.toList()) { option ->
                            val chosen = settings.accentColor == option
                            val swatch = androidx.compose.ui.graphics.Color(
                                if (palette.isDark) option.dark else option.light
                            )
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (chosen) 3.dp else 0.dp,
                                        color = palette.label.copy(alpha = if (chosen) 0.9f else 0f),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    )
                                    .clickable { settings.accentColor = option },
                            )
                        }
                    }
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Density",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = com.leo.imessage.ui.theme.Density.entries.map { it to it.label },
                        selected = settings.density,
                        onSelect = { settings.density = it },
                    )
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Double-Tap a Bubble",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = com.leo.imessage.ui.theme.DoubleTapAction.entries
                            .map { it to it.label },
                        selected = settings.doubleTapAction,
                        onSelect = { settings.doubleTapAction = it },
                    )
                }
                SettingsDivider()
                SettingsToggle("Colorful Bubbles", settings.colorfulBubbles) {
                    settings.colorfulBubbles = it
                }
                SettingsDivider()
                SettingsToggle("Notifications", settings.notificationsEnabled) {
                    settings.notificationsEnabled = it
                }
                SettingsDivider()
                SettingsToggle("Play Message Effects", settings.playEffects) {
                    settings.playEffects = it
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Motion",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = com.leo.imessage.ui.theme.MotionProfile.entries
                            .map { it to it.label },
                        selected = settings.motionProfile,
                        onSelect = { settings.motionProfile = it },
                    )
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Haptic Strength",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    com.leo.imessage.ui.components.SegmentedControl(
                        options = com.leo.imessage.ui.theme.HapticProfile.entries
                            .map { it to it.label },
                        selected = settings.hapticProfile,
                        onSelect = { settings.hapticProfile = it },
                    )
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
                SettingsRow(
                    "Version",
                    value = com.leo.imessage.BuildConfig.VERSION_NAME +
                        " (" + com.leo.imessage.BuildConfig.VERSION_CODE + ")",
                    showChevron = false,
                )
                SettingsDivider()
                SettingsRow(
                    "Built",
                    value = com.leo.imessage.util.buildDate(),
                    showChevron = false,
                )
                SettingsDivider()
                SettingsRow(
                    "What's New",
                    value = com.leo.imessage.BuildConfig.VERSION_NAME,
                    onClick = onOpenReleaseNotes,
                )
                SettingsDivider()
                SettingsRow(
                    "Backend",
                    // This said "Mock" for eighteen builds after the app
                    // started talking to Apple for real.
                    value = if (account == null) "Sample data" else "rustpush",
                    showChevron = false,
                )
                SettingsDivider()
                SettingsRow(
                    "Quick Replies",
                    value = "${settings.templates.size}",
                    onClick = { editingTemplates = true },
                )
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

        if (editingTemplates) {
            com.leo.imessage.ui.components.GlassPrompt(
                title = "Quick Replies",
                initial = settings.templates.joinToString("\n"),
                placeholder = "One per line",
                confirmLabel = "Save",
                onConfirm = { settings.templates = it.lines().filter { l -> l.isNotBlank() } },
                onDismiss = { editingTemplates = false },
            )
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
        appendLine("Relay diagnostics")
        appendLine(
            "App version: " + com.leo.imessage.BuildConfig.VERSION_NAME +
                " (" + com.leo.imessage.BuildConfig.VERSION_CODE + ")"
        )
        appendLine("Built: " + com.leo.imessage.util.buildDate())
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Theme: ${settings.themeMode}")
        appendLine("Bubble style: ${settings.bubbleStyle}")
        appendLine("Reduce motion: ${settings.lowPowerAnimations}")
        appendLine("Server: ${settings.relayServer.ifBlank { "not set" }}")
    }
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Relay diagnostics")
            putExtra(android.content.Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Export Diagnostics").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
