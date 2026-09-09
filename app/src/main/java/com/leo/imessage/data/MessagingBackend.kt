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

    suspend fun send(chatId: String, text: String, effect: MessageEffect = MessageEffect.NONE)

    suspend fun setTapback(messageId: String, kind: TapbackKind)

    suspend fun setEmojiTapback(messageId: String, emoji: String)

    /** Retracts a message you sent, keeping the text for one-tap reveal. */
    suspend fun unsend(messageId: String)

    suspend fun markRead(chatId: String)

    suspend fun setTyping(chatId: String, typing: Boolean)
}
