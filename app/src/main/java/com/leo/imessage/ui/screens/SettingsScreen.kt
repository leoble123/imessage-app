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
    /** Asks Apple about one address and reports what it said. */
    onCheckHandle: (suspend (String) -> String)? = null,
    /** Registers this device with Apple again. */
    onReRegister: (suspend () -> String)? = null,
    /** Asks Apple which devices it has registered on this account. */
    onRegistrationStatus: (suspend () -> String)? = null,
    /** Moves to a different registration relay, keeping the Apple ID session. */
    onSwitchRelay: (suspend (String, String) -> String)? = null,
    /** Adds or removes the conversations that aren't real. */
    onSampleConversations: (suspend (Boolean) -> String)? = null,
) {
    val palette = LocalPalette.current
    val hazeState = remember { HazeState() }
    val settings = com.leo.imessage.ui.theme.LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingTemplates by remember { mutableStateOf(false) }
    var checkingHandle by remember { mutableStateOf(false) }
    // Two prompts rather than one form: the host is asked for, then the code,
    // reusing the prompt that already exists instead of introducing a second
    // kind of dialog for one screen.
    var relayStep by remember { mutableStateOf(0) }
    var pendingRelayHost by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<String?>(null) }

    // Screen-scoped, and it has to be.
    //
    // This lived inside the `if (checkingHandle)` block that puts the prompt
    // on screen, which meant its lifetime was the prompt's. GlassPrompt calls
    // onConfirm and then dismisses itself, so tapping Check set
    // checkingHandle = false, the block left the composition, and the scope
    // was cancelled with the lookup still in flight - every time, before a
    // single request went out. The cancellation was then caught and shown as
    // though it were Apple's answer, which is how a network diagnostic spent
    // days reporting "The coroutine scope left the composition".
    val checkScope = rememberCoroutineScope()

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

            if (onSampleConversations != null) {
                val sampleScope = rememberCoroutineScope()
                var sampleStatus by remember { mutableStateOf<String?>(null) }
                ListSection(
                    header = "Testing",
                    footer = "Conversations that aren't real, for looking at the app when " +
                        "there is nothing in it. They never touch the network, and " +
                        "removing them leaves your own messages alone.",
                ) {
                    SettingsRow(
                        "Add Sample Conversations",
                        onClick = {
                            sampleStatus = "Adding…"
                            sampleScope.launch {
                                sampleStatus = runCatching { onSampleConversations(true) }
                                    .getOrElse { it.message ?: "That didn't work." }
                            }
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        "Remove Sample Conversations",
                        destructive = true,
                        onClick = {
                            sampleStatus = "Removing…"
                            sampleScope.launch {
                                sampleStatus = runCatching { onSampleConversations(false) }
                                    .getOrElse { it.message ?: "That didn't work." }
                            }
                        },
                    )
                    sampleStatus?.let {
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
            }

            ListSection(
                header = "Appearance",
                footer = "The weather backgrounds ask what the sky is doing where you " +
                    "are. They only ever read the position your phone already knew, and " +
                    "saying no just leaves the background following the clock.",
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Home Background",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.label,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    val locationPrompt =
                        androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts
                                .RequestPermission()
                        ) { }
                    com.leo.imessage.ui.components.BackgroundPicker(
                        selectedId = settings.homeBackground,
                        onSelect = { id ->
                            settings.homeBackground = id
                            // Asked for at the moment it is needed and not
                            // before, which is the only time the reason for
                            // it is obvious.
                            val wantsWeather =
                                id == com.leo.imessage.ui.theme.AdaptiveBackgrounds.WEATHER ||
                                    id == com.leo.imessage.ui.theme.AdaptiveBackgrounds.BOTH
                            if (wantsWeather &&
                                !com.leo.imessage.data.WeatherSource.hasPermission(context)
                            ) {
                                locationPrompt.launch(
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            }
                        },
                    )
                }
                SettingsDivider()
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
                if (onCheckHandle != null) {
                    // The one question a failed send cannot answer for you.
                    SettingsRow(
                        "Check iMessage Availability",
                        onClick = { checkingHandle = true },
                    )
                    checkResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryLabel,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, bottom = 12.dp,
                            ),
                        )
                    }
                    SettingsDivider()
                }
                if (onSwitchRelay != null) {
                    SettingsRow(
                        "Change Registration Server",
                        onClick = { relayStep = 1 },
                    )
                    Text(
                        "The server that supplies validation data, which is what Apple " +
                            "uses to decide whether this is a real machine. One running " +
                            "on genuine Apple hardware is treated differently from one " +
                            "running on an emulated Mac. This keeps you signed in - no " +
                            "password, no verification code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                    SettingsDivider()
                }
                if (onRegistrationStatus != null) {
                    // Deliberately above "Re-register": this is the check that
                    // says whether re-registering is the right move at all, and
                    // re-registering on a guess is what gets an account limited.
                    SettingsRow(
                        "Check Registration With Apple",
                        onClick = {
                            checkResult = "Asking Apple\u2026"
                            checkScope.launch {
                                checkResult = try {
                                    onRegistrationStatus()
                                } catch (cancel: kotlinx.coroutines.CancellationException) {
                                    throw cancel
                                } catch (e: Throwable) {
                                    e.message ?: "That didn't run."
                                }
                            }
                        },
                    )
                    Text(
                        "Apple's own list of the devices on this account. If this " +
                            "phone is missing from it, nothing will send or arrive " +
                            "and re-registering is the fix. If it is there, " +
                            "re-registering will not help.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                    SettingsDivider()
                }
                if (onReRegister != null) {
                    SettingsRow(
                        "Re-register with Apple",
                        onClick = {
                            checkResult = "Registering…"
                            checkScope.launch {
                                checkResult = try {
                                    onReRegister()
                                } catch (cancel: kotlinx.coroutines.CancellationException) {
                                    throw cancel
                                } catch (e: Throwable) {
                                    e.message ?: "That didn't run."
                                }
                            }
                        },
                    )
                    Text(
                        "Only if nothing sends or arrives. Apple keeps one registration " +
                            "per device, so if another iMessage app is signed in to this " +
                            "same account it will take it back and break that one instead. " +
                            "Registering over and over is what gets an account limited, so " +
                            "do this once and give it a minute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                    SettingsDivider()
                }
                SettingsRow(
                    "Export Diagnostics",
                    onClick = {
                        // Apple's device list goes in the report itself. The
                        // protocol log shows a lookup coming back empty but
                        // not why, and "why" is the whole question - so the
                        // export asks before it writes rather than leaving it
                        // to be run separately and pasted in by hand.
                        checkScope.launch {
                            // The log is read first, and that ordering is the
                            // whole point.
                            //
                            // Asking Apple for the device list logs the reply,
                            // and the reply is every registration on the
                            // account with its full client-data - tens of
                            // kilobytes on a single line, several times over.
                            // Reading the log afterwards meant the export was
                            // nothing but that dump: the one send it was
                            // captured to explain had been pushed out of the
                            // window by the diagnostic asking about it.
                            val log = recentCoreLog()
                            val registration = onRegistrationStatus?.let {
                                try {
                                    it()
                                } catch (cancel: kotlinx.coroutines.CancellationException) {
                                    throw cancel
                                } catch (e: Throwable) {
                                    "Couldn't ask Apple: ${e.message ?: e::class.java.simpleName}"
                                }
                            }
                            shareDiagnostics(context, settings, account, registration, log)
                        }
                    },
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

        if (relayStep == 1 && onSwitchRelay != null) {
            com.leo.imessage.ui.components.GlassPrompt(
                title = "Registration Server",
                initial = account?.relay ?: settings.relayServer,
                placeholder = "https://registration-relay.beeper.com",
                confirmLabel = "Next",
                onConfirm = { entered ->
                    pendingRelayHost = entered
                    relayStep = if (entered.isNotBlank()) 2 else 0
                },
                onDismiss = { if (relayStep == 1) relayStep = 0 },
            )
        }
        if (relayStep == 2 && onSwitchRelay != null) {
            com.leo.imessage.ui.components.GlassPrompt(
                title = "Pairing Code",
                initial = "",
                placeholder = "The code the Mac prints",
                confirmLabel = "Switch",
                onConfirm = { entered ->
                    if (entered.isNotBlank()) {
                        checkResult = "Switching\u2026 this re-registers, give it a moment."
                        val host = pendingRelayHost
                        checkScope.launch {
                            checkResult = try {
                                onSwitchRelay(host, entered)
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                throw cancel
                            } catch (e: Throwable) {
                                e.message ?: "That didn't run."
                            }
                        }
                    }
                },
                onDismiss = { if (relayStep == 2) relayStep = 0 },
            )
        }
        if (checkingHandle && onCheckHandle != null) {
            com.leo.imessage.ui.components.GlassPrompt(
                title = "Check iMessage Availability",
                initial = "",
                placeholder = "Phone number or email",
                confirmLabel = "Check",
                onConfirm = { entered ->
                    if (entered.isNotBlank()) {
                        checkResult = "Asking Apple…"
                        checkScope.launch {
                            checkResult = try {
                                onCheckHandle(entered)
                            } catch (cancel: kotlinx.coroutines.CancellationException) {
                                // Never reported as a result. A cancellation
                                // is this screen going away, not an answer,
                                // and dressing one up as the other is what
                                // hid the bug above.
                                throw cancel
                            } catch (e: Throwable) {
                                e.message ?: "That check didn't run."
                            }
                        }
                    }
                },
                onDismiss = { checkingHandle = false },
            )
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
    account: AccountSummary?,
    registration: String?,
    log: String,
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
        // The account's own relay, not settings.relayServer - that field is
        // the text box on the sign-in screen and is empty on a session
        // restored from disk, which made every exported report claim there
        // was no server configured while the app was plainly talking to one.
        appendLine("Server: ${account?.relay ?: settings.relayServer.ifBlank { "not set" }}")
        appendLine("Signed in as: ${account?.primaryHandle ?: "nobody"}")
        appendLine()
        registration?.let {
            appendLine("--- registration (Apple's answer) ---")
            appendLine(it)
            appendLine()
        }
        appendLine("--- protocol log ---")
        append(log)
    }
    // As a file, not as an intent extra.
    //
    // The report used to go in EXTRA_TEXT, which travels through a Binder
    // transaction with about a megabyte to share between everything in
    // flight. A few lines of version information fitted. A protocol log does
    // not, and the failure mode is not an error the user can see: the
    // transaction is rejected, the exception is swallowed by the runCatching
    // around it, and the button appears to do nothing at all.
    runCatching {
        val dir = java.io.File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = java.io.File(dir, "relay-diagnostics.txt")
        file.writeText(report)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Relay diagnostics")
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Export Diagnostics").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        // Visible. A diagnostic tool that fails silently is worse than none,
        // because it is indistinguishable from having nothing to report.
        android.widget.Toast.makeText(
            context,
            "Couldn't export: ${it::class.java.simpleName}: ${it.message}",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}

/**
 * The Rust core's own logcat output.
 *
 * Export Diagnostics used to carry the version, the theme and the device
 * model - none of which has ever been the reason a message failed to send.
 * The answers are in the protocol log, and an app is allowed to read back its
 * own logcat buffer, so there is no reason to need a cable and adb to see it.
 *
 * Bounded, and from the tail: the buffer holds far more than is useful and a
 * share sheet is not the place to discover that.
 *
 * A warning worth repeating wherever this ends up: at debug level these lines
 * carry push tokens and public keys for you and for whoever you looked up.
 * They are not passwords and they are not private keys, but they are
 * identifiers, so this belongs with someone helping you fix it and nowhere
 * else.
 */
private fun recentCoreLog(): String = runCatching {
    val process = ProcessBuilder(
        listOf("logcat", "-d", "-v", "time", "-t", "4000", "imessage-core:D", "*:S")
    ).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    // Truncate per line before truncating the whole thing.
    //
    // A few of these lines are enormous - a registration dump or a key
    // blob runs to tens of kilobytes on one line - and the tail is taken in
    // characters, so a handful of them can be the entire export while the
    // hundreds of short lines that actually say what happened fall off the
    // front. 1500 characters keeps every header of a signed request,
    // x-id-self-uri included, and throws away the payload nobody reads.
    if (text.isBlank()) {
        "(nothing logged yet - try the action that fails, then export again)"
    } else {
        text.lineSequence()
            .map { if (it.length > 1500) it.take(1500) + " …[truncated]" else it }
            .joinToString("\n")
            .takeLast(200_000)
    }
}.getOrElse { "(couldn't read the log: ${it.message})" }
