package com.leo.imessage.data

import kotlinx.coroutines.flow.Flow

/**
 * Everything the UI needs from a messaging source.
 *
 * The UI is written against this interface only, so the real rustpush-backed
 * implementation can be dropped in behind it without the screens changing.
 * [MockBackend] implements it fully today so the app runs and can be designed
 * against before the Rust core is wired up.
 */
interface MessagingBackend {
    val chats: Flow<List<Chat>>

    fun messages(chatId: String): Flow<List<Message>>

    /**
     * Current values, read synchronously.
     *
     * Collecting a Flow always yields one frame of the `initial` value first,
     * which renders an empty screen and then pops content in - that single
     * frame is what reads as a "flash" when opening a thread. These let the
     * first composition start from real data instead.
     */
    fun chatsNow(): List<Chat>

    fun messagesNow(chatId: String): List<Message>

    suspend fun send(
        chatId: String,
        text: String,
        effect: MessageEffect = MessageEffect.NONE,
        replyToId: String? = null,
        attachments: List<Attachment> = emptyList(),
    )

    /** Removes a message locally, the way Messages' Delete action does. */
    suspend fun delete(messageId: String)

    suspend fun setBookmarked(messageId: String, bookmarked: Boolean)

    suspend fun setMessagePinned(messageId: String, pinned: Boolean)

    /** A private note on a message. Null clears it. */
    suspend fun setNote(messageId: String, note: String?)

    /** Reminds you about a message at a wall-clock time. Null clears it. */
    suspend fun setReminder(messageId: String, at: Long?)

    /** Queues a message to send at a given time. */
    suspend fun scheduleSend(chatId: String, text: String, at: Long)

    /** Sends a scheduled message immediately, or cancels it outright. */
    suspend fun resolveScheduled(messageId: String, send: Boolean)

    suspend fun sendPoll(chatId: String, question: String, options: List<String>)

    /** Casts or withdraws a vote. Single-choice polls move the vote. */
    suspend fun votePoll(messageId: String, optionId: String)

    /** Edits a message you sent, keeping the prior text in its history. */
    suspend fun edit(messageId: String, newText: String)

    suspend fun setTapback(messageId: String, kind: TapbackKind)

    suspend fun setEmojiTapback(messageId: String, emoji: String)

    /** Retracts a message you sent, keeping the text for one-tap reveal. */
    suspend fun unsend(messageId: String)

    suspend fun setPinned(chatId: String, pinned: Boolean)

    suspend fun setMuted(chatId: String, muted: Boolean)

    /** Archived chats leave the main list without being deleted. */
    suspend fun setArchived(chatId: String, archived: Boolean)

    /** Removes a conversation and everything in it. */
    suspend fun deleteChat(chatId: String)

    suspend fun markRead(chatId: String)

    /** Puts the unread dot back, the way Messages' Mark as Unread does. */
    suspend fun markUnread(chatId: String)

    suspend fun setTyping(chatId: String, typing: Boolean)

    /**
     * Opens a conversation with these handles, returning its chat id.
     *
     * Returns an existing chat when there already is one, so typing a number
     * you've messaged before lands you in that thread rather than starting a
     * second, parallel copy of it.
     *
     * Throws if none of the handles can receive iMessage - which is worth
     * knowing before a message is typed, not after it fails to send.
     */
    suspend fun startChat(handles: List<String>): String

    /**
     * Sends a failed message again.
     *
     * Without this a failure is terminal - the text is on screen but there is
     * no way to act on it except retyping, which for a long message people
     * simply won't do.
     */
    suspend fun retry(messageId: String)
}
