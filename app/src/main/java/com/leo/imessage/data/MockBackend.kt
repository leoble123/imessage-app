package com.leo.imessage.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.random.Random

/**
 * In-memory backend with realistic sample threads, so the whole UI is
 * exercisable (and designable) before the rustpush core is connected.
 *
 * It also simulates the parts that make a messenger feel alive - send
 * transitions from sending -> delivered, typing indicators, and the odd
 * incoming reply - so animations and state changes can be seen for real
 * rather than only in static screenshots.
 */
class MockBackend : MessagingBackend {

    private val me = Contact("me", "Me", "me@icloud.com")

    private val priya = Contact("c1", "Priya Raman", "priya@icloud.com")
    private val dev = Contact("c2", "Dev Shah", "+15555550142")
    private val mom = Contact("c3", "Mom", "mom@icloud.com")
    private val jordan = Contact("c4", "Jordan Lee", "jordan@icloud.com")
    private val sam = Contact("c5", "Sam Ortiz", "+15555550188")
    private val casey = Contact("c6", "Casey Kim", "casey@icloud.com")

    private val now = System.currentTimeMillis()
    private fun minsAgo(m: Long) = now - m * 60_000
    private fun hoursAgo(h: Long) = now - h * 3_600_000
    private fun daysAgo(d: Long) = now - d * 86_400_000

    private val _messages = MutableStateFlow(seedMessages())
    private val _chats = MutableStateFlow(seedChats())

    override val chats: Flow<List<Chat>> = _chats.asStateFlow().map { list ->
        list.sortedWith(
            compareByDescending<Chat> { it.isPinned }
                .thenByDescending { it.lastMessage?.timestamp ?: 0 }
        )
    }

    override fun messages(chatId: String): Flow<List<Message>> =
        _messages.asStateFlow().map { all ->
            all.filter { it.chatId == chatId }.sortedBy { it.timestamp }
        }

    override fun chatsNow(): List<Chat> = _chats.value.sortedWith(
        compareByDescending<Chat> { it.isPinned }
            .thenByDescending { it.lastMessage?.timestamp ?: 0 }
    )

    override fun messagesNow(chatId: String): List<Message> =
        _messages.value.filter { it.chatId == chatId }.sortedBy { it.timestamp }

    override suspend fun edit(messageId: String, newText: String) {
        updateMessage(messageId) { msg ->
            msg.copy(
                text = newText,
                editHistory = msg.editHistory + msg.text,
                editedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isPinned = pinned) else it
        }
    }

    override suspend fun setMuted(chatId: String, muted: Boolean) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isMuted = muted) else it
        }
    }

    override suspend fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filterNot { it.id == chatId }
        _messages.value = _messages.value.filterNot { it.chatId == chatId }
    }

    override suspend fun delete(messageId: String) {
        _messages.value = _messages.value.filterNot { it.id == messageId }
    }

    override suspend fun send(
        chatId: String,
        text: String,
        effect: MessageEffect,
        replyToId: String?,
        attachments: List<Attachment>,
    ) {
        val msg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            senderId = me.id,
            deliveryState = DeliveryState.SENDING,
            effect = effect,
            replyToId = replyToId,
            attachments = attachments,
        )
        _messages.value = _messages.value + msg
        touchChat(chatId, msg)

        // Simulated send lifecycle, so the receipt animation is visible.
        delay(600)
        updateMessage(msg.id) { it.copy(deliveryState = DeliveryState.SENT) }
        delay(500)
        updateMessage(msg.id) { it.copy(deliveryState = DeliveryState.DELIVERED) }

        maybeAutoReply(chatId)
    }

    /** Occasionally answers back so typing indicators and inbound bubbles show up live. */
    private suspend fun maybeAutoReply(chatId: String) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val responder = chat.participants.firstOrNull() ?: return
        if (Random.nextInt(100) > 65) return

        delay(700)
        setTyping(chatId, true)
        delay(1500 + Random.nextLong(1200))
        setTyping(chatId, false)

        val replies = listOf(
            "ok that works", "haha fair", "on it", "yeah for sure",
            "wait really?", "lemme check", "sounds good", "😂",
            "be there in 10", "just saw this, sorry",
        )
        val reply = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            text = replies.random(),
            timestamp = System.currentTimeMillis(),
            isFromMe = false,
            senderId = responder.id,
            service = chat.service,
        )
        _messages.value = _messages.value + reply
        touchChat(chatId, reply)
    }

    override suspend fun setTapback(messageId: String, kind: TapbackKind) {
        updateMessage(messageId) { msg ->
            val existing = msg.tapbacks.firstOrNull { it.fromMe }
            val without = msg.tapbacks.filterNot { it.fromMe }
            if (existing?.kind == kind) {
                msg.copy(tapbacks = without)
            } else {
                msg.copy(tapbacks = without + Tapback(kind, fromMe = true, senderId = me.id))
            }
        }
    }

    override suspend fun setEmojiTapback(messageId: String, emoji: String) {
        updateMessage(messageId) { msg ->
            val without = msg.tapbacks.filterNot { it.fromMe }
            val existing = msg.tapbacks.firstOrNull { it.fromMe }
            if (existing?.emoji == emoji) {
                msg.copy(tapbacks = without)
            } else {
                msg.copy(
                    tapbacks = without + Tapback(
                        TapbackKind.ANY_EMOJI,
                        fromMe = true,
                        senderId = me.id,
                        emoji = emoji,
                    )
                )
            }
        }
    }

    override suspend fun unsend(messageId: String) {
        updateMessage(messageId) { msg ->
            msg.copy(unsentText = msg.text, isUnsent = true, text = "")
        }
    }

    override suspend fun markRead(chatId: String) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(unreadCount = 0) else it
        }
    }

    override suspend fun setTyping(chatId: String, typing: Boolean) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isTyping = typing) else it
        }
    }

    private fun updateMessage(id: String, transform: (Message) -> Message) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    private fun touchChat(chatId: String, msg: Message) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(lastMessage = msg) else it
        }
    }

    private fun seedChats(): List<Chat> {
        val msgs = _messages.value
        fun last(chatId: String) = msgs.filter { it.chatId == chatId }.maxByOrNull { it.timestamp }
        return listOf(
            Chat("ch1", "Priya Raman", listOf(priya), last("ch1"), isPinned = true),
            Chat("ch2", "Dev Shah", listOf(dev), last("ch2"), unreadCount = 2, isPinned = true),
            Chat("ch3", "Weekend Plans", listOf(jordan, casey, sam), last("ch3"), unreadCount = 5),
            Chat("ch4", "Mom", listOf(mom), last("ch4")),
            Chat("ch5", "Sam Ortiz", listOf(sam), last("ch5"), service = Service.SMS),
            Chat("ch6", "Casey Kim", listOf(casey), last("ch6"), isMuted = true),
        )
    }

    private fun seedMessages(): List<Message> {
        var n = 0
        fun id() = "m${n++}"

        return listOf(
            // --- Priya: recent, shows tapbacks + read receipt + unsent ---
            Message("ch1-q", "ch1", "did you see the new place on 5th?", minsAgo(52), false, priya.id),
            Message(id(), "ch1", "the one with the patio?", minsAgo(50), true, me.id),
            Message(
                id(), "ch1", "yes!! it's so nice inside", minsAgo(49), false, priya.id,
                tapbacks = listOf(Tapback(TapbackKind.HEART, fromMe = true, senderId = "me")),
            ),
            Message(
                id(), "ch1", "", minsAgo(48), false, priya.id,
                attachments = listOf(
                    Attachment("a1", "IMG_4821.HEIC", "image/heic"),
                    Attachment("a2", "IMG_4822.HEIC", "image/heic"),
                ),
            ),
            Message(id(), "ch1", "ok that patio is unreal", minsAgo(47), true, me.id),
            // Exactly one reply, so it stays in the transcript carrying a
            // dimmed copy of the original rather than collapsing to a link.
            Message(
                id(), "ch1", "walked past it twice and never noticed",
                minsAgo(46), true, me.id, replyToId = "ch1-q",
            ),
            Message(
                id(), "ch1", "ok don't tell dev about the surprise yet",
                minsAgo(24), false, priya.id,
                unsentText = "ok don't tell dev about the surprise yet",
            ),
            Message(id(), "ch1", "wait what surprise 👀", minsAgo(23), true, me.id),
            Message(id(), "ch1", "nothing!! forget i said anything", minsAgo(22), false, priya.id),
            Message(
                id(), "ch1", "too late", minsAgo(21), true, me.id,
                deliveryState = DeliveryState.READ,
                tapbacks = listOf(Tapback(TapbackKind.HAHA, fromMe = false, senderId = priya.id)),
            ),

            // --- Dev: unread, shows effects ---
            Message(id(), "ch2", "pushed the fix, can you review?", hoursAgo(3), false, dev.id),
            Message(id(), "ch2", "CI is green finally 🎉", hoursAgo(3), false, dev.id, effect = MessageEffect.SLAM),
            Message(id(), "ch2", "nice, looking now", hoursAgo(2), true, me.id, deliveryState = DeliveryState.READ),
            Message(id(), "ch2", "left two small comments, otherwise LGTM", minsAgo(96), true, me.id),
            Message(id(), "ch2", "got em, fixing", minsAgo(41), false, dev.id),
            Message(id(), "ch2", "should be good now", minsAgo(38), false, dev.id),

            // --- Group thread ---
            Message("ch3-r", "ch3", "so are we doing saturday or sunday", hoursAgo(7), false, jordan.id),
            Message(id(), "ch3", "saturday works better for me", hoursAgo(7), false, casey.id),
            Message(id(), "ch3", "same", hoursAgo(6), false, sam.id),
            Message(id(), "ch3", "saturday it is", hoursAgo(6), true, me.id, deliveryState = DeliveryState.READ),
            Message(
                id(), "ch3", "i'll book the table for 7", hoursAgo(5), false, jordan.id,
                tapbacks = listOf(
                    Tapback(TapbackKind.THUMBS_UP, fromMe = true, senderId = "me"),
                    Tapback(TapbackKind.THUMBS_UP, fromMe = false, senderId = casey.id),
                ),
            ),
            Message(id(), "ch3", "perfect", minsAgo(140), false, casey.id),
            Message(id(), "ch3", "can't wait 🙌", minsAgo(58), false, sam.id),
            Message(
                id(), "ch3", "", minsAgo(52), false, casey.id,
                attachments = listOf(Attachment("a3", "menu.jpg", "image/jpeg")),
            ),
            // A reply thread hanging off the opening question. These stay
            // out of the transcript - the original carries "4 Replies".
            Message(
                id(), "ch3", "sunday is better for me tbh", hoursAgo(7), false, casey.id,
                replyToId = "ch3-r",
            ),
            Message(
                id(), "ch3", "i can do either honestly", hoursAgo(7), true, me.id,
                replyToId = "ch3-r", deliveryState = DeliveryState.READ,
            ),
            Message(
                id(), "ch3", "sunday i've got my sister's thing though",
                hoursAgo(6), false, sam.id, replyToId = "ch3-r",
            ),
            Message(
                id(), "ch3", "ok saturday then, settled", hoursAgo(6), false, jordan.id,
                replyToId = "ch3-r",
                tapbacks = listOf(Tapback(TapbackKind.THUMBS_UP, fromMe = true, senderId = "me")),
            ),

            // --- Mom ---
            Message(id(), "ch4", "call me when you get a chance sweetie", daysAgo(1), false, mom.id),
            Message(id(), "ch4", "will do! in meetings till 4", daysAgo(1), true, me.id, deliveryState = DeliveryState.READ),
            Message(id(), "ch4", "no rush ❤️", hoursAgo(20), false, mom.id),

            // --- SMS (green) ---
            Message(id(), "ch5", "hey it's sam from the gym", daysAgo(2), false, sam.id, service = Service.SMS),
            Message(id(), "ch5", "hey! whats up", daysAgo(2), true, me.id, service = Service.SMS),
            Message(id(), "ch5", "you still got that spare lock?", daysAgo(2), false, sam.id, service = Service.SMS),

            // --- Casey ---
            Message(id(), "ch6", "sent you the files", daysAgo(4), false, casey.id),
            Message(id(), "ch6", "got them, thanks!", daysAgo(4), true, me.id, deliveryState = DeliveryState.READ),
        )
    }
}
