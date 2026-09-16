package com.leo.imessage.data

import android.util.Log
import com.leo.imessage.data.BlueBubblesMapping.objects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * A [MessagingBackend] on top of a BlueBubbles server.
 *
 * The other backend makes this phone an iMessage device in its own right,
 * which means registering with Apple and proving the hardware is real. This
 * one doesn't talk to Apple at all: a Mac that is already a legitimate
 * iMessage client does that, and this drives it over HTTP. Nothing here can
 * be rate limited, refused, or de-registered, because from Apple's side
 * nothing here exists.
 *
 * Local-only features - bookmarks, notes, reminders, pins, polls - go
 * straight to the store, exactly as they do on the other backend. They were
 * never network features and there is nothing on a Mac to ask about them.
 *
 * Updates arrive by polling rather than the socket their own client uses.
 * Socket.IO would mean a dependency, and polling a handful of endpoints every
 * few seconds is both sufficient for a conversation and far harder to get
 * subtly wrong. The cost is latency measured in seconds; the benefit is that
 * a dropped connection is simply the next poll.
 */
class BlueBubblesBackend(
    private val client: BlueBubblesClient,
    private val store: MessageStore,
    private val contacts: Contacts? = null,
    /** Needed to read a picked file out of its content:// URI before upload. */
    private val context: android.content.Context? = null,
    /** How often to ask the server what's new. */
    private val pollIntervalMs: Long = 3_000L,
) : MessagingBackend {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The conversation on screen, so its unread count isn't raised under you. */
    @Volatile
    var openChatId: String? = null

    /**
     * The newest message timestamp already stored.
     *
     * Every poll asks only for what is newer than this, so the cost of the
     * loop is one small request rather than a re-read of every conversation.
     */
    @Volatile
    private var watermark: Long = 0L

    /** Outgoing messages awaiting their server guid, keyed by tempGuid. */
    private val pending = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    var lastError: String? = null
        private set

    // --- reads (all local; the poll is what fills the store) -------------

    override val chats: Flow<List<Chat>> = store.chats

    override fun messages(chatId: String): Flow<List<Message>> =
        store.chats.map { store.messages(chatId) }

    override fun chatsNow(): List<Chat> = store.chatsNow()

    override fun messagesNow(chatId: String): List<Message> = store.messages(chatId)

    override fun recentMessages(chatId: String): List<Message> =
        store.messages(chatId, limit = 60)

    // --- lifecycle -------------------------------------------------------

    /**
     * Pulls the conversation list, then keeps it current.
     *
     * The first sync is the expensive one and happens once; after that the
     * loop only asks for messages newer than the newest it has.
     */
    fun start() {
        scope.launch {
            runCatching { syncChats() }
                .onFailure { note("Couldn't load conversations", it) }
            while (isActive) {
                runCatching { pollOnce() }
                    .onFailure { note("Lost contact with the server", it) }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun note(what: String, e: Throwable) {
        lastError = "$what: ${e.message ?: e::class.java.simpleName}"
        Log.w(TAG, lastError!!)
    }

    // --- syncing ---------------------------------------------------------

    /** The conversation list, with a screenful of history for each. */
    private suspend fun syncChats() = withContext(Dispatchers.IO) {
        val chats = client.chats().objects()
        for (json in chats) {
            val mapped = BlueBubblesMapping.chat(json, store.chat(json.optString("guid"))) ?: continue
            store.addChat(mapped)
            runCatching {
                val page = client.messages(mapped.id, limit = 50).objects()
                ingest(page, mapped.id)
            }.onFailure { Log.d(TAG, "history for ${mapped.id}: ${it.message}") }
        }
        lastError = null
    }

    /** One round of "anything new?". */
    private suspend fun pollOnce() = withContext(Dispatchers.IO) {
        // A first run with nothing stored has no watermark to ask from, so it
        // asks for the last few minutes rather than the whole database.
        val since = if (watermark > 0L) watermark else System.currentTimeMillis() - 5 * 60_000L
        val fresh = client.messagesSince(since).objects()
        if (fresh.isEmpty()) {
            lastError = null
            return@withContext
        }
        // Group by conversation so each message lands in the right thread.
        for (json in fresh) {
            val chatGuid = json.optJSONArray("chats")?.objects()?.firstOrNull()?.optString("guid")
                ?: continue
            if (store.chat(chatGuid) == null) {
                // A conversation started elsewhere since the last sync.
                runCatching {
                    client.chats().objects()
                        .firstOrNull { it.optString("guid") == chatGuid }
                        ?.let { BlueBubblesMapping.chat(it) }
                        ?.let { store.addChat(it) }
                }
            }
            ingest(listOf(json), chatGuid)
        }
        lastError = null
    }

    /**
     * Stores a page of payloads.
     *
     * Tapbacks and retractions come down the same endpoint as messages and
     * have to be applied *to* a message rather than added as ones, which is
     * the only real subtlety in here.
     */
    private suspend fun ingest(payloads: List<JSONObject>, chatId: String) {
        for (json in payloads) {
            val ts = BlueBubblesMapping.timestamp(json.optLong("dateCreated", 0L))
            if (ts > watermark) watermark = ts

            if (BlueBubblesMapping.isTapback(json)) {
                applyTapback(json)
                continue
            }

            val mapped = BlueBubblesMapping.message(json, chatId) { client.attachmentUrl(it) }
                ?: continue

            // Our own message coming back round. Adopt the server's guid onto
            // the local copy instead of showing the message twice.
            val temp = pending.remove(json.optString("tempGuid"))
            if (temp != null) {
                store.adoptGuid(temp, mapped.id, mapped.deliveryState)
                continue
            }

            if (store.hasMessage(mapped.id)) {
                store.updateMessage(mapped.id) {
                    it.copy(
                        deliveryState = mapped.deliveryState,
                        text = if (mapped.isUnsent) it.text else mapped.text,
                        unsentText = mapped.unsentText ?: it.unsentText,
                        editedAt = mapped.editedAt ?: it.editedAt,
                    )
                }
            } else {
                val unread = !mapped.isFromMe && chatId != openChatId
                store.insert(mapped, incrementUnread = unread)
            }
        }
    }

    private suspend fun applyTapback(json: JSONObject) {
        val target = BlueBubblesMapping.tapbackTarget(json) ?: return
        val kind = BlueBubblesMapping.tapbackKind(json.optInt("associatedMessageType") % 1000 + 2000)
            ?: return
        val fromMe = json.optBoolean("isFromMe", false)
        val sender = json.optJSONObject("handle")?.optString("address").orEmpty()
        val removed = BlueBubblesMapping.tapbackRemoved(json)

        store.updateMessage(target) { message ->
            val without = message.tapbacks.filterNot {
                it.kind == kind && it.fromMe == fromMe && it.senderId == sender
            }
            message.copy(
                tapbacks = if (removed) without
                else without + Tapback(kind = kind, fromMe = fromMe, senderId = sender),
            )
        }
    }

    // --- sending ---------------------------------------------------------

    override suspend fun send(
        chatId: String,
        text: String,
        effect: MessageEffect,
        replyToId: String?,
        attachments: List<Attachment>,
        mentions: List<String>,
    ) {
        val localId = "local-${UUID.randomUUID()}"
        val tempGuid = "relay-${UUID.randomUUID()}"
        pending[tempGuid] = localId

        // On screen immediately, as Messages does, then reconciled by the poll.
        store.insert(
            Message(
                id = localId,
                chatId = chatId,
                text = text,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderId = null,
                deliveryState = DeliveryState.SENDING,
                attachments = attachments,
                effect = effect,
                replyToId = replyToId,
            ),
        )

        try {
            withContext(Dispatchers.IO) {
                // Files first, one request each - the server takes a single
                // attachment per call - then the text, so a caption lands
                // under its picture rather than above it.
                uploadAll(chatId, attachments)
                if (text.isNotBlank() || attachments.isEmpty()) {
                    client.sendText(
                        chatGuid = chatId,
                        tempGuid = tempGuid,
                        text = text,
                        effectId = effectId(effect),
                        replyToGuid = replyToId,
                    )
                }
            }
            store.updateMessage(localId) { it.copy(deliveryState = DeliveryState.SENT) }
        } catch (e: Throwable) {
            pending.remove(tempGuid)
            note("Couldn't send", e)
            store.updateMessage(localId) { it.copy(deliveryState = DeliveryState.FAILED) }
        }
    }

    /**
     * Uploads each picked file.
     *
     * Staged to a file this app owns first, the same way the other backend
     * does it: a content:// URI from the picker is a permission grant that can
     * be revoked the moment the picker closes, and reading it lazily during an
     * upload that may take a minute is how a large video fails halfway.
     */
    private fun uploadAll(chatId: String, attachments: List<Attachment>) {
        if (attachments.isEmpty()) return
        val ctx = context ?: run {
            Log.w(TAG, "no context: cannot upload ${attachments.size} attachment(s)")
            return
        }
        for (attachment in attachments) {
            val uri = attachment.uri ?: continue
            val staged = AttachmentFiles.stage(ctx, android.net.Uri.parse(uri), attachment.id)
                ?: continue
            client.sendAttachment(
                chatGuid = chatId,
                tempGuid = "relay-${UUID.randomUUID()}",
                file = java.io.File(staged.path),
                fileName = staged.name,
                mimeType = staged.mimeType,
            )
        }
    }

    private fun effectId(effect: MessageEffect): String? = when (effect) {
        MessageEffect.NONE -> null
        MessageEffect.SLAM -> "com.apple.MobileSMS.expressivesend.impact"
        MessageEffect.LOUD -> "com.apple.MobileSMS.expressivesend.loud"
        MessageEffect.GENTLE -> "com.apple.MobileSMS.expressivesend.gentle"
        MessageEffect.INVISIBLE_INK -> "com.apple.MobileSMS.expressivesend.invisibleink"
    }

    override suspend fun retry(messageId: String) {
        val message = store.message(messageId) ?: return
        store.deleteMessage(messageId)
        send(message.chatId, message.text, message.effect, message.replyToId, message.attachments)
    }

    override suspend fun edit(messageId: String, newText: String) {
        val message = store.message(messageId) ?: return
        withContext(Dispatchers.IO) { runCatching { client.edit(messageId, newText) } }
        store.updateMessage(messageId) {
            it.copy(
                text = newText,
                editHistory = it.editHistory + message.text,
                editedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun unsend(messageId: String) {
        val message = store.message(messageId) ?: return
        withContext(Dispatchers.IO) { runCatching { client.unsend(messageId) } }
        store.updateMessage(messageId) { it.copy(text = "", unsentText = message.text) }
    }

    override suspend fun setTapback(messageId: String, kind: TapbackKind) {
        val message = store.message(messageId) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                client.react(
                    chatGuid = message.chatId,
                    messageGuid = messageId,
                    messageText = message.text,
                    reaction = BlueBubblesMapping.reactionName(kind),
                )
            }
        }
        store.updateMessage(messageId) {
            val without = it.tapbacks.filterNot { t -> t.fromMe }
            it.copy(tapbacks = without + Tapback(kind = kind, fromMe = true, senderId = ""))
        }
    }

    /** iMessage has no arbitrary-emoji tapback over this API; nearest wins. */
    override suspend fun setEmojiTapback(messageId: String, emoji: String) {
        store.updateMessage(messageId) {
            val without = it.tapbacks.filterNot { t -> t.fromMe }
            it.copy(
                tapbacks = without +
                    Tapback(kind = TapbackKind.ANY_EMOJI, fromMe = true, senderId = "", emoji = emoji),
            )
        }
    }

    override suspend fun startChat(handles: List<String>): String {
        val existing = store.chatsNow().firstOrNull { chat ->
            chat.participants.map { it.handle }.toSet() == handles.toSet()
        }
        if (existing != null) return existing.id

        val created = withContext(Dispatchers.IO) { client.newChat(handles, null) }
        val guid = created?.optString("guid")
        if (guid.isNullOrBlank()) {
            throw IllegalStateException(
                lastError ?: "The server couldn't start that conversation.",
            )
        }
        BlueBubblesMapping.chat(created)?.let { store.addChat(it) }
        return guid
    }

    override suspend fun markRead(chatId: String) {
        store.updateChat(chatId) { it.copy(unreadCount = 0) }
        withContext(Dispatchers.IO) { runCatching { client.markRead(chatId) } }
    }

    override suspend fun markUnread(chatId: String) {
        store.updateChat(chatId) { it.copy(unreadCount = maxOf(1, it.unreadCount)) }
        withContext(Dispatchers.IO) { runCatching { client.markUnread(chatId) } }
    }

    override suspend fun setTyping(chatId: String, typing: Boolean) {
        withContext(Dispatchers.IO) { client.setTyping(chatId, typing) }
    }

    // --- scheduled sends and polls --------------------------------------
    //
    // Neither is an iMessage feature, so neither goes near the Mac. A
    // scheduled message is a local placeholder with a timer on it; a poll is
    // a local card that sends readable text, so it doesn't land on a real
    // iPhone as an empty bubble.

    override suspend fun scheduleSend(chatId: String, text: String, at: Long) {
        val id = "scheduled-${UUID.randomUUID()}"
        store.insert(
            Message(
                id = id,
                chatId = chatId,
                text = text,
                timestamp = at,
                isFromMe = true,
                senderId = null,
                deliveryState = DeliveryState.SENDING,
                scheduledFor = at,
            ),
        )
        armScheduled(id, at)
    }

    /**
     * Waits out a scheduled send.
     *
     * The timer only lives as long as the process, so [start] would have to
     * sweep the store on launch for these to survive a restart. Until then a
     * scheduled message sent while the app is closed goes late rather than
     * never - the sweep is the follow-up, not this.
     */
    private fun armScheduled(id: String, at: Long) {
        scope.launch {
            delay((at - System.currentTimeMillis()).coerceAtLeast(0))
            if (store.message(id)?.scheduledFor != null) {
                resolveScheduled(id, send = true)
            }
        }
    }

    override suspend fun resolveScheduled(messageId: String, send: Boolean) {
        val message = store.message(messageId) ?: return
        store.deleteMessage(messageId)
        if (send) {
            send(message.chatId, message.text, message.effect, message.replyToId)
        }
    }

    override suspend fun sendPoll(chatId: String, question: String, options: List<String>) {
        val poll = Poll(
            question = question,
            options = options.filter { it.isNotBlank() }
                .map { PollOption(UUID.randomUUID().toString(), it.trim()) },
        )
        val body = buildString {
            append(question)
            poll.options.forEachIndexed { i, option -> append("\n${i + 1}. ${option.label}") }
        }
        val localId = "poll-${UUID.randomUUID()}"
        store.insert(
            Message(
                id = localId,
                chatId = chatId,
                text = "",
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderId = null,
                deliveryState = DeliveryState.SENDING,
                poll = poll,
            ),
        )
        runCatching {
            withContext(Dispatchers.IO) {
                client.sendText(chatId, "relay-${UUID.randomUUID()}", body)
            }
        }.onSuccess {
            store.updateMessage(localId) { it.copy(deliveryState = DeliveryState.SENT) }
        }.onFailure {
            note("Couldn't send the poll", it)
            store.updateMessage(localId) { it.copy(deliveryState = DeliveryState.FAILED) }
        }
    }

    override suspend fun votePoll(messageId: String, optionId: String) {
        // No identity from the server side here, so votes are attributed to a
        // fixed local id - enough for the card to render your own choice.
        val me = "me"
        store.updateMessage(messageId) { msg ->
            val poll = msg.poll ?: return@updateMessage msg
            val already = poll.options.firstOrNull { it.id == optionId }?.voters?.contains(me) == true
            msg.copy(
                poll = poll.copy(
                    options = poll.options.map { option ->
                        when {
                            option.id == optionId && already -> option.copy(voters = option.voters - me)
                            option.id == optionId -> option.copy(voters = option.voters + me)
                            !poll.allowsMultiple -> option.copy(voters = option.voters - me)
                            else -> option
                        }
                    },
                ),
            )
        }
    }

    // --- local only ------------------------------------------------------

    override suspend fun delete(messageId: String) = store.deleteMessage(messageId)

    override suspend fun setBookmarked(messageId: String, bookmarked: Boolean) {
        store.updateMessage(messageId) { it.copy(isBookmarked = bookmarked) }
    }

    override suspend fun setMessagePinned(messageId: String, pinned: Boolean) {
        store.updateMessage(messageId) { it.copy(isPinned = pinned) }
    }

    override suspend fun setNote(messageId: String, note: String?) {
        store.updateMessage(messageId) { it.copy(note = note) }
    }

    override suspend fun setReminder(messageId: String, at: Long?) {
        store.updateMessage(messageId) { it.copy(remindAt = at) }
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        store.updateChat(chatId) { it.copy(isPinned = pinned) }
    }

    override suspend fun setMuted(chatId: String, muted: Boolean) {
        store.updateChat(chatId) { it.copy(isMuted = muted) }
    }

    override suspend fun setArchived(chatId: String, archived: Boolean) {
        store.updateChat(chatId) { it.copy(isArchived = archived) }
    }

    override suspend fun deleteChat(chatId: String) = store.deleteChat(chatId)

    companion object {
        private const val TAG = "BlueBubbles"
    }
}
