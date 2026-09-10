package com.leo.imessage

import android.app.Application
import com.leo.imessage.data.AccountManager

/**
 * Holds the account for the life of the process, not the Activity.
 *
 * The push connection is the reason this exists. It lives inside the Rust
 * core, which the [AccountManager] owns; if that were an Activity field it
 * would be torn down and rebuilt on every rotation, and every rebuild is a
 * fresh connection to Apple. Hanging it off the Application means one
 * connection per process, which is what it should be.
 */
class EchoApp : Application() {
    val account: AccountManager by lazy { AccountManager(this) }

    /**
     * What the user is currently looking at, if anything.
     *
     * The notifier needs both halves: a message shouldn't raise a
     * notification for a thread that's open on screen, but it absolutely
     * should for the same thread when the phone is in a pocket.
     */
    @Volatile
    var isVisible: Boolean = false

    @Volatile
    var visibleChatId: String? = null

    /**
     * Read straight from preferences rather than through the settings object
     * the UI holds: the notifier runs when there's no UI, so there's no
     * composition-scoped instance to ask.
     */
    fun settingsAllowNotifications(): Boolean =
        com.leo.imessage.ui.theme.SettingsStore(this).let {
            com.leo.imessage.ui.theme.AppSettings(it).notificationsEnabled
        }

    companion object {
        /** Reachable from the notification receiver, which has no Activity. */
        lateinit var instance: EchoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Installed first, so a crash during the rest of startup is still
        // captured. This app is sideloaded with no tools attached, so without
        // it a crash report is "it closed".
        CrashReporter.install(this)
    }
}
