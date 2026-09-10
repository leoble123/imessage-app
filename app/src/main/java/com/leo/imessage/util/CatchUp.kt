package com.leo.imessage.util

import com.leo.imessage.data.Chat
import com.leo.imessage.data.Message

/**
 * What happened while you were away.
 *
 * There is no model behind this and it does not pretend there is - it counts
 * and groups, which is most of what you actually want. "12 messages from 3
 * people, 2 questions, 4 photos" answers "do I need to read this now" far
 * better than a paraphrase would, and unlike a paraphrase it cannot be
 * wrong about what was said.
 *
 * Questions get pulled out because an unanswered question is the one thing
 * in a backlog that is genuinely waiting on you.
 */
data class CatchUp(
    val messageCount: Int,
    val people: List<String>,
    val questions: List<Message>,
    val mediaCount: Int,
    val links: List<Message>,
    val firstUnreadIndex: Int,
) {
    val isEmpty: Boolean get() = messageCount == 0

    fun headline(): String = when {
        messageCount == 0 -> "You're all caught up"
        people.size <= 1 -> "$messageCount new " + plural(messageCount, "message")
        else -> "$messageCount new " + plural(messageCount, "message") +
            " from ${people.size} people"
    }

    private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
}

fun buildCatchUp(chat: Chat, messages: List<Message>): CatchUp {
    val unreadCount = chat.unreadCount
    // Nothing marks individual messages read, so the unread count defines the
    // tail. Approximate rather than invent per-message read state we do not
    // have - and an approximation that is obviously an approximation is safer
    // than a precise-looking number that is quietly wrong.
    val recent = messages.takeLast(unreadCount.coerceAtLeast(0))
        .filter { !it.isFromMe }

    val firstUnread = (messages.size - unreadCount).coerceIn(0, (messages.size - 1).coerceAtLeast(0))

    return CatchUp(
        messageCount = recent.size,
        people = recent.mapNotNull { m ->
            chat.participants.firstOrNull { it.id == m.senderId }?.displayName
        }.distinct(),
        questions = recent.filter { it.text.contains('?') },
        mediaCount = recent.sumOf { it.attachments.size },
        links = recent.filter {
            it.text.contains("http", ignoreCase = true) ||
                it.text.contains("www.", ignoreCase = true)
        },
        firstUnreadIndex = firstUnread,
    )
}
