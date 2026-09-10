package com.leo.imessage.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.leo.imessage.data.MessagingBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles Reply and Mark as Read from the notification shade.
 *
 * The point of an inline reply is that you never leave what you were doing,
 * so this runs entirely in a receiver - no activity, no window. It talks to
 * the same backend the UI does, so a reply sent from the shade lands in the
 * transcript exactly as if it had been typed in the app.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(Notifier.EXTRA_CHAT_ID) ?: return
        // Prefer the account's own backend: a reply can arrive long after the
        // Activity is gone, and the field below is only set while it's alive.
        val backend = currentBackend() ?: return
        val pending = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_MARK_READ -> backend.markRead(chatId)

                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                        if (text.isNotEmpty()) {
                            backend.send(chatId = chatId, text = text)
                            backend.markRead(chatId)
                        }
                    }
                }
                Notifier.clear(context, chatId)
            } finally {
                // Always finish the broadcast, or the system kills the
                // process mid-send and the reply silently never happens.
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.leo.imessage.REPLY"
        const val ACTION_MARK_READ = "com.leo.imessage.MARK_READ"
        const val KEY_REPLY_TEXT = "reply_text"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Set by the Activity while it's running.
         *
         * Only used as a fallback now - it's how the sample-data backend is
         * reachable, since that one exists nowhere else. A real account's
         * backend is found through the Application instead, which outlives
         * the Activity and is what makes replying from the shade work when
         * the app isn't open.
         */
        @Volatile
        var backendProvider: MessagingBackend? = null

        private fun currentBackend(): MessagingBackend? =
            (com.leo.imessage.EchoApp.instance.account.state.value
                as? com.leo.imessage.data.AccountState.Ready)?.backend
                ?: backendProvider
    }
}
