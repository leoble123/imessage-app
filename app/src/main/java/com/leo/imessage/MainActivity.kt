package com.leo.imessage

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.leo.imessage.data.AccountManager
import com.leo.imessage.data.AccountState
import com.leo.imessage.ui.setup.SetupScreen
import com.leo.imessage.ui.AppRoot
import com.leo.imessage.ui.theme.iMessageTheme

class MainActivity : ComponentActivity() {

    /**
     * Which conversation to open on launch.
     *
     * Held as state rather than read once, so a notification tapped while the
     * app is already running reopens the right thread through onNewIntent
     * instead of dropping you wherever you happened to be.
     */
    private val pendingChatId = androidx.compose.runtime.mutableStateOf<String?>(null)

    /**
     * Sign-in and the live backend, shared with the connection service.
     *
     * Held by the Application rather than here: the push connection lives
     * inside it, and an Activity-scoped copy would tear down and reconnect on
     * every rotation.
     */
    private val account: AccountManager get() = (application as EchoApp).account
    private val settings by lazy {
        com.leo.imessage.ui.theme.AppSettings(
            com.leo.imessage.ui.theme.SettingsStore(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        com.leo.imessage.notify.Notifier.ensureChannel(this)
        // Numbers get typed the way people say them - "555 123 4567" - with no
        // country in them, and Apple only answers to full international form.
        // The SIM is the only reliable source for the missing part.
        com.leo.imessage.data.Handles.defaultRegion = detectRegion()
        requestPermissions()

        // Reconnect with the saved relay and registration, if there are any,
        // so a relaunch goes straight to the conversation list.
        lifecycleScope.launch {
            if (account.resume()) {
                // Only once there's something to keep alive - starting the
                // service before that would put a "Connected" notification in
                // the shade for an account that doesn't exist yet.
                com.leo.imessage.notify.ConnectionService.start(this@MainActivity)
            }
        }
        pendingChatId.value = intent?.getStringExtra(
            com.leo.imessage.notify.Notifier.EXTRA_CHAT_ID
        )

        setContent {
            iMessageTheme(settings) {
                // Before anything else: if the last run ended badly, say so
                // and hand over the trace rather than starting up as if
                // nothing happened.
                var crash by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(CrashReporter.lastCrash(this))
                }
                crash?.let { report ->
                    com.leo.imessage.ui.CrashScreen(report) {
                        CrashReporter.clear(this)
                        crash = null
                    }
                    return@iMessageTheme
                }

                val state by account.state.collectAsState()
                // Declared here so the overlay below can see it regardless of
                // which branch rendered.
                val liveCall = (state as? AccountState.Ready)
                    ?.let { it.backend as? com.leo.imessage.data.RustBackend }
                    ?.calls

                val activeCall by (liveCall?.current
                    ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) })
                    .collectAsState()

                // Setup and the app proper are separate trees rather than one
                // with a flag: the conversation screens require a backend, and
                // giving them a placeholder one to satisfy the type would mean
                // every screen carrying a "not connected yet" branch.
                when (val current = state) {
                    is AccountState.Ready -> {
                        val backend = current.backend as? com.leo.imessage.data.RustBackend
                        // Lets notification Reply / Mark as Read reach the same
                        // backend the UI uses, so a reply from the shade lands
                        // in the transcript.
                        com.leo.imessage.notify.NotificationActionReceiver
                            .backendProvider = current.backend
                        AppRoot(
                            backend = current.backend,
                            openChatRequest = pendingChatId.value,
                            onChatRequestHandled = { pendingChatId.value = null },
                            accountSummary = account.summary(),
                            addressBook = account.contacts.all(),
                            onPlaceCall = { targets ->
                                lifecycleScope.launch {
                                    runCatching { backend?.calls?.place(targets, video = false) }
                                }
                            },
                            onCheckHandle = { raw -> account.checkHandle(raw) },
                            onSampleConversations = { add ->
                                if (add) account.addSampleConversations()
                                else account.removeSampleConversations()
                            },
                            onImport = { uri ->
                                val r = account.importFromOpenBubbles(uri)
                                if (r.messages == 0 && r.duplicates > 0) {
                                    "Already imported - nothing new."
                                } else {
                                    buildString {
                                        append("Imported ${r.messages} messages")
                                        if (r.chats > 0) append(" across ${r.chats} conversations")
                                        if (r.attachments > 0) append(", ${r.attachments} attachments")
                                        append(".")
                                        if (r.duplicates > 0) append(" Skipped ${r.duplicates} already here.")
                                    }
                                }
                            },
                            onSignOut = {
                                lifecycleScope.launch {
                                    account.signOut()
                                    com.leo.imessage.notify.ConnectionService
                                        .stop(this@MainActivity)
                                }
                            },
                        )
                    }
                    else -> SetupScreen(
                        state = current,
                        account = account,
                        // Finishing setup is the moment there's a connection
                        // worth keeping, so that's when the service starts.
                        onConnected = {
                            com.leo.imessage.notify.ConnectionService.start(this)
                        },
                    )
                }

                // Above everything, including setup. A ringing phone is the
                // most interruptive thing the app does and must not be hidden
                // behind whatever screen happened to be open.
                activeCall?.let { call ->
                    com.leo.imessage.ui.screens.CallScreen(
                        call = call,
                        onAnswer = { lifecycleScope.launch { liveCall?.answer() } },
                        onDecline = { lifecycleScope.launch { liveCall?.decline() } },
                        onHangUp = { lifecycleScope.launch { liveCall?.hangUp() } },
                        onDismiss = { liveCall?.dismiss() },
                        onMuteChange = { liveCall?.muted = it },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.leo.imessage.notify.Notifier.EXTRA_CHAT_ID)?.let {
            pendingChatId.value = it
        }
    }

    override fun onPause() {
        super.onPause()
        (application as EchoApp).isVisible = false
        // Stops read receipts going out for messages arriving while the phone
        // is in a pocket on whatever thread was last open.
        account.backend?.appVisible = false
        // The typing reporter's own timeout clears the bubble from here,
        // now that it no longer runs on the composition's scope.
        // Android can kill the process the moment the app is backgrounded, so
        // anything still buffered has to reach disk here rather than later.
        lifecycleScope.launch { account.flush() }
    }

    override fun onResume() {
        super.onResume()
        (application as EchoApp).isVisible = true
        account.backend?.appVisible = true
        // Some launchers/OEM power paths reset the mode when the window is
        // re-shown, so re-assert it rather than only asking once.
        requestHighestRefreshRate()
    }

    /**
     * Asks for the two permissions the app can't do its job without.
     *
     * Contacts is the one that matters most: without it every conversation is
     * titled with a raw phone number. Notifications is Android 13+ only.
     */
    private fun requestPermissions() {
        val wanted = buildList {
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(android.Manifest.permission.READ_CONTACTS)
            }
            // Asked up front rather than mid-call: a permission dialog while
            // the phone is ringing is the worst possible moment for one.
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(android.Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
    }

    @Deprecated("Superseded by the Activity Result APIs, which this screen doesn't use.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val gotContacts = permissions.indexOf(android.Manifest.permission.READ_CONTACTS)
            .takeIf { it >= 0 }
            ?.let { grantResults.getOrNull(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
            ?: false
        // The backend is already running and threads are already on screen by
        // the time this comes back, so they have to be re-titled rather than
        // just picked up on next launch.
        if (gotContacts) lifecycleScope.launch { account.reloadContacts() }
    }

    /** The SIM's country, falling back to the device locale. */
    private fun detectRegion(): String {
        val telephony = getSystemService(android.telephony.TelephonyManager::class.java)
        val fromSim = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        return (fromSim ?: resources.configuration.locales[0].country).uppercase()
            .ifBlank { "US" }
    }

    /**
     * Asks the compositor for the panel's fastest mode.
     *
     * Android does *not* give apps the high-refresh mode by default. On
     * adaptive-refresh panels (the S24's 120Hz LTPO among them) the system
     * parks the display at 60Hz and only steps up for apps that ask - so
     * without this every animation in the app is capped at 60fps no matter
     * how well tuned it is, which is exactly the "60hz" feel.
     *
     * Only modes at the current resolution are considered: switching
     * resolution mid-session would force a full surface reallocation and a
     * visible flicker.
     */
    private fun requestHighestRefreshRate() {
        val display: Display? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
            else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val active = display?.mode ?: return

        val best = display.supportedModes
            .filter {
                it.physicalWidth == active.physicalWidth &&
                    it.physicalHeight == active.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return

        window.attributes = window.attributes.apply {
            preferredDisplayModeId = best.modeId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                preferredRefreshRate = best.refreshRate
            }
        }
    }
}
