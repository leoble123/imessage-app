package com.leo.imessage.util

import com.leo.imessage.data.Chat

/**
 * What is waiting across every conversation, rather than inside one.
 *
 * The per-conversation Catch Up answers "what did I miss in here". This
 * answers the question you actually have while looking at the list, which is
 * "is any of this for me" - and it answers it by counting, not by
 * paraphrasing. A count cannot be wrong about what was said.
 *
 * Built from the conversation list alone. Every number here comes off a
 * `Chat` and its newest message, so opening this reads nothing from the
 * database: it has to be instant, because it is reached by a gesture people
 * will make idly.
 */
data class InboxSummary(
    val unreadTotal: Int,
    /** The conversations with something in them, newest first. */
    val waiting: List<Chat>,
    /** Conversations whose newest message asks something. */
    val questions: List<Chat>,
    /** When the longest-waiting conversation last had something arrive. */
    val oldestWaitingAt: Long?,
) {
    val isEmpty: Boolean get() = unreadTotal == 0

    fun headline(): String = when {
        unreadTotal == 0 -> "You're all caught up"
        waiting.size == 1 -> "$unreadTotal unread from ${waiting.first().displayName}"
        else -> "$unreadTotal unread in ${waiting.size} conversations"
    }
}

fun buildInboxSummary(chats: List<Chat>): InboxSummary {
    val waiting = chats
        .filter { it.unreadCount > 0 && !it.isArchived }
        .sortedByDescending { it.lastMessage?.timestamp ?: 0L }

    return InboxSummary(
        unreadTotal = waiting.sumOf { it.unreadCount },
        waiting = waiting,
        // An unanswered question is the one thing in a backlog genuinely
        // waiting on you, so it gets counted separately from the noise.
        questions = waiting.filter { it.lastMessage?.text?.contains('?') == true },
        oldestWaitingAt = waiting.minOfOrNull { it.lastMessage?.timestamp ?: 0L }
            ?.takeIf { it > 0L },
    )
}
