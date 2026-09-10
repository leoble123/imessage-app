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
    )

    /** Edits a message you sent, keeping the prior text in its history. */
    suspend fun edit(messageId: String, newText: String)

    suspend fun setTapback(messageId: String, kind: TapbackKind)

    suspend fun setEmojiTapback(messageId: String, emoji: String)

    /** Retracts a message you sent, keeping the text for one-tap reveal. */
    suspend fun unsend(messageId: String)

    suspend fun markRead(chatId: String)

    suspend fun setTyping(chatId: String, typing: Boolean)
}
