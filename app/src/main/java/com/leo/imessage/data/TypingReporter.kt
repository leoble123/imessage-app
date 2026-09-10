package com.leo.imessage.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tells the other side when you're typing, and when you've stopped.
 *
 * iMessage has no "still typing" heartbeat: you send one message when the
 * bubble should appear and another when it should go away. So the state has to
 * be tracked here, and the stop has to be sent explicitly - forget it and the
 * three dots sit on their screen indefinitely, which is worse than never
 * showing them at all.
 *
 * Keystrokes are not sent one for one. Each one is a real protocol message,
 * and typing a sentence would put out a few dozen.
 */
class TypingReporter(
    private val backend: MessagingBackend,
    private val scope: CoroutineScope,
) {
    private var activeChat: String? = null
    private var stopJob: Job? = null

    fun onTyping(chatId: String, hasText: Boolean) {
        if (!hasText) {
            stop()
            return
        }

        // Only the first keystroke of a run sends anything.
        if (activeChat != chatId) {
            // Moved to a different conversation mid-type: clear the old one,
            // or its bubble never goes away.
            activeChat?.let { previous -> scope.launch { runCatching { backend.setTyping(previous, false) } } }
            activeChat = chatId
            scope.launch { runCatching { backend.setTyping(chatId, true) } }
        }

        // Sliding deadline: the bubble clears a few seconds after the last
        // keystroke, the way it does on a real device.
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(STOP_AFTER_MS)
            stop()
        }
    }

    /** Called when the draft is cleared, sent, or the conversation is left. */
    fun stop() {
        stopJob?.cancel()
        stopJob = null
        val chat = activeChat ?: return
        activeChat = null
        scope.launch { runCatching { backend.setTyping(chat, false) } }
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
