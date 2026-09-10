package com.leo.imessage.data

import android.telephony.PhoneNumberUtils

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

    /**
     * The region used to expand a local phone number, e.g. "US".
     *
     * Set once from the SIM at startup. A number typed the way people actually
     * type it - "555 123 4567" - has no country in it, and Apple only ever
     * answers to full international form, so something has to supply the
     * missing part.
     */
    @Volatile
    var defaultRegion: String = "US"

    /**
     * Puts a handle into the form Apple uses: `mailto:` for addresses,
     * `tel:` plus an E.164 number for phones.
     *
     * Getting this wrong doesn't fail loudly - IDS simply reports no keys for
     * the handle, which reads as "this person isn't on iMessage" even when
     * they are. That was the bug: a number typed as "5551234567" became
     * `tel:5551234567`, and one typed as "(555) 123-4567" got no prefix at
     * all, so every lookup but the user's own address came back empty.
     */
    fun normalize(handle: String): String {
        val trimmed = handle.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("tel:") || trimmed.startsWith("mailto:")) return trimmed
        if (trimmed.contains('@')) return "mailto:$trimmed"

        // Anything that's mostly digits is treated as a number, so the spaces,
        // dashes and brackets people paste in don't defeat it.
        val digits = trimmed.count { it.isDigit() }

        // Short codes are not phone numbers and have no country: pushing one
        // through E.164 turns "62966" into "+162966", which matches nothing.
        if (trimmed.all { it.isDigit() } && digits in 3..6) {
            return "tel:$trimmed"
        }

        if (digits >= 7 && trimmed.all { it.isDigit() || it in "+()- ." }) {
            // Android's own formatter knows every country's rules; the
            // alternative is a hand-rolled table that is wrong somewhere.
            val e164 = runCatching {
                PhoneNumberUtils.formatNumberToE164(trimmed, defaultRegion)
            }.getOrNull()
            if (e164 != null) return "tel:$e164"

            // Fall back to a reasonable guess rather than sending something
            // Apple certainly won't match.
            val bare = trimmed.filter { it.isDigit() }
            return when {
                trimmed.startsWith("+") -> "tel:+$bare"
                bare.length > 10 -> "tel:+$bare"
                else -> "tel:+1$bare"
            }
        }
        return trimmed
    }

    /** Strips the scheme for display, and prettifies numbers. */
    fun display(handle: String): String {
        val bare = handle.removePrefix("tel:").removePrefix("mailto:")
        if (!bare.startsWith("+")) return bare
        return runCatching { PhoneNumberUtils.formatNumber(bare, defaultRegion) }
            .getOrNull() ?: bare
    }

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
