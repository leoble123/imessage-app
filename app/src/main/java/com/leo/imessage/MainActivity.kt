package com.leo.imessage

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leo.imessage.data.MockBackend
import com.leo.imessage.ui.AppRoot
import com.leo.imessage.ui.theme.iMessageTheme

class MainActivity : ComponentActivity() {

    // Swapped for the rustpush-backed implementation once the Rust core is
    // wired in; every screen is written against the MessagingBackend
    // interface, so nothing above this line changes when that happens.
    private val backend by lazy { MockBackend() }
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
        // Lets notification Reply / Mark as Read reach the same backend the
        // UI uses, so a reply from the shade lands in the transcript.
        com.leo.imessage.notify.NotificationActionReceiver.backendProvider = backend
        requestNotificationPermission()
        setContent {
            iMessageTheme(settings) {
                AppRoot(backend)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Some launchers/OEM power paths reset the mode when the window is
        // re-shown, so re-assert it rather than only asking once.
        requestHighestRefreshRate()
    }

    /** Android 13+ makes notifications an opt-in the user has to grant. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
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
