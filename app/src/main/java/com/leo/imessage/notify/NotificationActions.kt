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
        val backend = backendProvider ?: return
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
         * The live backend, handed over by the app on start.
         *
         * A receiver is constructed by the framework and cannot be given
         * constructor arguments, and the in-memory backend has no other way
         * to be reached from outside the activity. When the rustpush core
         * lands this becomes a real service binding instead.
         */
        @Volatile
        var backendProvider: MessagingBackend? = null
    }
}
