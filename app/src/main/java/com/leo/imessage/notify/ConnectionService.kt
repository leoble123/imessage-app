package com.leo.imessage.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.leo.imessage.EchoApp
import com.leo.imessage.MainActivity
import com.leo.imessage.data.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the iMessage connection up while the app isn't open.
 *
 * Without this the app only receives messages while you're looking at it:
 * Android stops the process shortly after it goes to the background, the push
 * connection goes with it, and anything sent meanwhile arrives whenever you
 * next open the app. That's the difference between a viewer and a messenger.
 *
 * It has to be a *foreground* service with a visible notification, because
 * that's the only category Android lets hold a socket open indefinitely. There
 * is no push-without-a-process option here: iMessage delivers over Apple's own
 * APNs connection, not Firebase, so nothing else can wake us on its behalf.
 * The notification is set to the lowest importance that still satisfies the
 * requirement, so it sits silently at the bottom of the shade.
 */
class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification("Connected"))

        scope.launch {
            EchoApp.instance.account.state.collectLatest { state ->
                when (state) {
                    is AccountState.Ready -> {
                        update("Connected")
                        watchForMessages(state)
                    }
                    // Signed out from Settings - there's nothing left to hold
                    // a connection for, so stop rather than sit in the shade.
                    AccountState.NeedsRelay, AccountState.NeedsSignIn -> stopSelf()
                    else -> update("Connecting…")
                }
            }
        }
    }

    /**
     * Raises notifications for anything that arrives.
     *
     * This lives here rather than in the UI because the UI isn't running most
     * of the time a notification matters. Suspends for as long as the account
     * is connected, which is what collectLatest above wants - a new account
     * state cancels it and starts a fresh watch.
     */
    private suspend fun watchForMessages(state: AccountState.Ready) {
        val app = EchoApp.instance
        var lastSeen: String? = null
        state.backend.chats.collect { chats ->
            val newest = chats.mapNotNull { it.lastMessage }.maxByOrNull { it.timestamp }
                ?: return@collect
            // Keyed on the message, not the chat list, so pinning or muting
            // doesn't re-announce something already seen.
            if (newest.id == lastSeen) return@collect
            lastSeen = newest.id

            if (newest.isFromMe) return@collect
            if (!app.settingsAllowNotifications()) return@collect
            // Already on screen - the message is right there.
            if (app.isVisible && app.visibleChatId == newest.chatId) return@collect

            chats.firstOrNull { it.id == newest.chatId }
                ?.takeUnless { it.isMuted }
                ?.let { Notifier.post(this, it, newest) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if Android kills us under memory pressure, come back.
        // Reconnecting costs a round trip; staying down costs every message
        // sent in the meantime.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Echo")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Connection",
                // MIN keeps it out of the way: no sound, no badge, collapsed
                // at the bottom of the shade. It can't be removed entirely -
                // Android requires a foreground service to show something.
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps iMessage connected so messages arrive."
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
