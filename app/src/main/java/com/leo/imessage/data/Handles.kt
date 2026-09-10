package com.leo.imessage.data

/**
 * Translating between iMessage handles and the app's chat identity.
 *
 * iMessage has no concept of a "chat id" for one-to-one conversations. A
 * message carries a participant list, and it's up to the client to decide that
 * two messages belong to the same thread. Groups do have a stable identifier
 * (`sender_guid`), which survives renames and participant changes.
 *
 * So chats are keyed two ways, and the distinction matters: derive a group's
 * key from its participants and adding someone silently forks the thread in
 * two.
 */
object Handles {

    /** Apple prefixes phone numbers with `tel:` and addresses with `mailto:`. */
    fun normalize(handle: String): String {
        val trimmed = handle.trim()
        return when {
            trimmed.startsWith("tel:") || trimmed.startsWith("mailto:") -> trimmed
            trimmed.contains('@') -> "mailto:$trimmed"
            // A leading + or an all-digits string is a phone number. Anything
            // else is left alone rather than guessed at - sending to a
            // mis-prefixed handle fails in a way that's hard to read.
            trimmed.startsWith("+") || trimmed.all { it.isDigit() } -> "tel:$trimmed"
            else -> trimmed
        }
    }

    /** Strips the scheme for display. */
    fun display(handle: String): String =
        handle.removePrefix("tel:").removePrefix("mailto:")

    /**
     * The key for a conversation.
     *
     * Groups use their GUID. One-to-one chats use the other participant's
     * handle, sorted so that the key doesn't depend on which side sent the
     * message that created it.
     */
    fun chatId(participants: List<String>, groupGuid: String?): String {
        val others = participants.map(::normalize).distinct().sorted()
        return if (others.size > 1 && groupGuid != null) {
            "group:$groupGuid"
        } else {
            "chat:${others.joinToString(";")}"
        }
    }

    /** A stable contact id derived from the handle, so avatars stay put. */
    fun contactId(handle: String): String = "handle:${normalize(handle)}"

    fun contact(handle: String, name: String? = null): Contact {
        val normalized = normalize(handle)
        return Contact(
            id = contactId(normalized),
            displayName = name ?: display(normalized),
            handle = normalized,
        )
    }
}
