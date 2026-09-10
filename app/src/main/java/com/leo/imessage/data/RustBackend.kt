package com.leo.imessage.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uniffi.imessage_core.CoreException
import uniffi.imessage_core.EventKind
import uniffi.imessage_core.EventListener
import uniffi.imessage_core.ImessageCore
import uniffi.imessage_core.IncomingEvent
import java.util.UUID

/**
 * The real backend: the app's [MessagingBackend] on top of the rustpush core.
 *
 * The split is deliberate. Rust owns the protocol - the push connection, the
 * cryptographic identity, encoding and decoding. Kotlin owns everything the
 * protocol doesn't have an opinion about: what a conversation *is*, what order
 * things happened in, and the pile of local-only features (bookmarks, notes,
 * reminders, polls, scheduled sends) that never touch the network at all.
 *
 * That means a good half of this class doesn't call into Rust and shouldn't:
 * pinning a chat or saving a note is a local edit, and routing it through the
 * protocol would be inventing traffic Apple's servers don't expect.
 */
class RustBackend(
    private val core: ImessageCore,
    private val store: MessageStore,
    /**
     * The address book, so threads are titled with names instead of numbers.
     * Optional so the backend still works before permission is granted.
     */
    private val contacts: Contacts? = null,
    /** Microphone and earpiece for calls. Null keeps calls signalling-only. */
    private val audio: CallAudio? = null,
    /** Needed to stage outgoing files and store downloaded ones. */
    private val context: android.content.Context? = null,
) : MessagingBackend {

    /**
     * FaceTime, sharing this backend's connection.
     *
     * It lives here because call events arrive down the same push connection
     * as messages and are delivered through the same listener - splitting them
     * across two objects would mean two listeners racing over one socket.
     */
    val calls: Calls = Calls(core, contacts, audio)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _chats = MutableStateFlow(store.chats)
    private val _messages = MutableStateFlow(store.messages)

    /**
     * The conversation currently on screen, if any.
     *
     * Set by the UI. Without it, a message arriving in the thread you are
     * reading still raises the unread badge - and nothing clears it, because
     * the read receipt only fires when you *enter* a conversation.
     */
    @Volatile
    var openChatId: String? = null

    /** This account's own handles, filled in once the core is started. */
    @Volatile
    private var myHandles: List<String> = emptyList()

    @Volatile
    private var myHandle: String? = null

    private val me get() = Contact("me", "Me", myHandle ?: "me")

    /** This account's own addresses and numbers, for Settings to show. */
    fun handles(): List<String> = myHandles

    /**
     * A contact for a handle, named from the address book when it's in there.
     *
     * Everything that builds a participant goes through here - otherwise half
     * the app shows "Priya" and the other half "+15555550142", which looks
     * like two different people.
     */
    private fun contactFor(handle: String): Contact =
        Handles.contact(handle, contacts?.nameFor(handle))

    /**
     * Re-reads the store after something outside the backend changed it, such
     * as an import. The flows hold a snapshot, so without this the new rows
     * exist on disk and nowhere on screen.
     */
    fun reloadFromStore() {
        _chats.value = store.chats
        _messages.value = store.messages
    }

    /** Re-titles existing chats after contacts load or permission is granted. */
    suspend fun refreshContactNames() {
        val source = contacts ?: return
        if (!source.loaded) return
        mutate { chats, messages ->
            chats.map { chat ->
                val named = chat.participants.map { contactFor(it.handle) }
                val title = if (chat.id.startsWith("group:") && chat.displayName.isNotBlank() &&
                    chat.participants.none { it.displayName == chat.displayName }
                ) {
                    // A group's own name is not derived from participants, so
                    // it must not be overwritten by them.
                    chat.displayName
                } else {
                    named.joinToString(", ") { it.displayName }
                }
                chat.copy(participants = named, displayName = title)
            } to messages
        }
    }

    override val chats: Flow<List<Chat>> = _chats.asStateFlow().map(::sortChats)

    override fun messages(chatId: String): Flow<List<Message>> =
        _messages.asStateFlow().map { all ->
            // distinctBy is deliberate belt-and-braces. The transcript keys
            // its rows by message id, so a duplicate is not a display glitch -
            // it takes the whole screen down.
            all.filter { it.chatId == chatId }
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
        }

    override fun chatsNow(): List<Chat> = sortChats(_chats.value)

    override fun messagesNow(chatId: String): List<Message> =
        _messages.value.filter { it.chatId == chatId }
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

    private fun sortChats(list: List<Chat>) = list.distinctBy { it.id }.sortedWith(
        compareByDescending<Chat> { it.isPinned }
            .thenByDescending { it.lastMessage?.timestamp ?: 0 }
    )

    /**
     * Starts the receive loop. Safe to call more than once - the core ignores
     * a second start.
     */
    suspend fun start() {
        // Clears out conversations left by that bug. They can neither send nor
        // receive, so there is nothing to preserve - and one sitting in the
        // list looks like a real thread that has simply stopped working.
        mutate { chats, messages ->
            val broken = chats.filter { it.participants.isEmpty() }.map { it.id }.toSet()
            if (broken.isEmpty()) return@mutate chats to messages
            Log.i(TAG, "removing ${broken.size} conversation(s) with no participants")
            chats.filterNot { it.id in broken } to messages.filterNot { it.chatId in broken }
        }
        core.start(Listener())

        // Re-arm anything that was queued when the app last closed. One whose
        // time has already passed goes immediately - late is closer to what
        // was asked for than never.
        _messages.value
            .filter { it.scheduledFor != null && it.isFromMe }
            .forEach { armScheduled(it.id, it.scheduledFor!!) }

        myHandles = runCatching { core.handles() }.getOrNull()?.all.orEmpty()
        myHandle = myHandles.firstOrNull()
        // So we aren't listed as a participant in our own call.
        calls.myHandles = myHandles
    }

    // --- Sending ------------------------------------------------------------

    override suspend fun send(
        chatId: String,
        text: String,
        effect: MessageEffect,
        replyToId: String?,
        attachments: List<Attachment>,
    ) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        // A conversation with nobody in it can't be sent to. This used to be
        // reachable: a message addressed only to your own handle had that
        // handle filtered out as "us", leaving a participant-less thread that
        // accepted messages and delivered them nowhere.
        if (chat.sendTargets().isEmpty()) {
            Log.e(TAG, "refusing to send to a conversation with no participants")
            return
        }
        val localId = UUID.randomUUID().toString()

        // The bubble appears before the network is touched. Waiting on the
        // round trip would put a visible stall between the send tap and the
        // bubble, which is the single most noticeable thing a messenger can
        // get wrong.
        val pending = Message(
            id = localId,
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
        append(pending)

        try {
            // Attachments are uploaded before the message goes out, so this
            // path is a different call rather than an extra argument. Sending
            // them through sendText was the bug: the files stayed local and
            // the recipient got the text alone, with nothing to show it went
            // wrong.
            val guid = if (attachments.isEmpty()) {
                core.sendText(
                    participants = chat.sendTargets(),
                    groupName = chat.groupName(),
                    senderGuid = chat.groupGuid(),
                    text = text,
                    replyToId = replyToId,
                    replyToPart = null,
                    effect = effect.wireName(),
                )
            } else {
                val staged = stageForSending(attachments)
                if (staged.isEmpty()) {
                    throw IllegalStateException("Couldn't read those files.")
                }
                core.sendAttachments(
                    participants = chat.sendTargets(),
                    groupName = chat.groupName(),
                    senderGuid = chat.groupGuid(),
                    text = text,
                    files = staged,
                    replyToId = replyToId,
                    effect = effect.wireName(),
                )
            }
            // Adopt the GUID Apple assigned. Tapbacks, edits and unsends all
            // address a message by it, so a local id that never gets replaced
            // produces a message nobody can react to.
            replaceId(localId, guid, DeliveryState.SENT)
        } catch (e: Exception) {
            // Not just CoreException: staging a file can fail with an ordinary
            // IO error, and a message stuck on "sending" forever is worse than
            // one that says it failed.
            Log.e(TAG, "send failed", e)
            updateMessage(localId) { it.copy(deliveryState = DeliveryState.FAILED) }
        }
    }

    /**
     * Copies picked files somewhere the Rust side can open them.
     *
     * Android's content URIs are permission-scoped handles owned by another
     * app; the protocol code takes a filesystem path, and the grant can be
     * revoked as soon as the picker closes.
     */
    private fun stageForSending(
        attachments: List<Attachment>,
    ): List<uniffi.imessage_core.OutgoingFile> {
        val context = context ?: return emptyList()
        return attachments.mapNotNull { attachment ->
            val uri = attachment.uri ?: return@mapNotNull null
            val staged = AttachmentFiles.stage(
                context,
                android.net.Uri.parse(uri),
                attachment.id,
            ) ?: return@mapNotNull null
            uniffi.imessage_core.OutgoingFile(
                path = staged.path,
                name = staged.name,
                mimeType = staged.mimeType,
                utiType = staged.utiType,
            )
        }
    }

    /**
     * Fetches a received attachment's bytes and points the message at them.
     *
     * Incoming attachments are references until asked for, so this is what
     * turns a placeholder into a picture.
     */
    suspend fun downloadAttachment(messageId: String, attachmentId: String) {
        val context = context ?: return
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val index = message.attachments.indexOfFirst { it.id == attachmentId }
        if (index < 0) return
        val attachment = message.attachments[index]
        if (attachment.uri != null) return

        val target = java.io.File(
            AttachmentFiles.dir(context),
            "$messageId-$index-${attachment.fileName}",
        )
        try {
            core.downloadAttachment(messageId, index.toUInt(), target.absolutePath)
            updateMessage(messageId) { msg ->
                msg.copy(
                    attachments = msg.attachments.mapIndexed { i, a ->
                        if (i == index) a.copy(uri = android.net.Uri.fromFile(target).toString())
                        else a
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "couldn't download ${attachment.fileName}", e)
            runCatching { target.delete() }
        }
    }

    override suspend fun setTapback(messageId: String, kind: TapbackKind) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val existing = message.tapbacks.firstOrNull { it.fromMe }
        // Tapping the tapback you already gave takes it back, the way iOS does.
        val removing = existing?.kind == kind
        applyTapback(message, kind, null, added = !removing)
        sendTapback(message, kind.wireName(), added = !removing)
    }

    override suspend fun setEmojiTapback(messageId: String, emoji: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val existing = message.tapbacks.firstOrNull { it.fromMe }
        val removing = existing?.emoji == emoji
        applyTapback(message, TapbackKind.ANY_EMOJI, emoji, added = !removing)
        sendTapback(message, emoji, added = !removing)
    }

    private suspend fun sendTapback(message: Message, reaction: String, added: Boolean) {
        val chat = _chats.value.firstOrNull { it.id == message.chatId } ?: return
        runCatching {
            core.sendTapback(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
                targetId = message.id,
                targetPart = 0uL,
                // iMessage quotes the message being reacted to in the tapback
                // itself; that's what devices without tapback support show.
                targetText = message.text,
                reaction = reaction,
                added = added,
            )
        }.onFailure { Log.e(TAG, "tapback failed", it) }
    }

    private suspend fun applyTapback(
        message: Message,
        kind: TapbackKind,
        emoji: String?,
        added: Boolean,
    ) {
        updateMessage(message.id) { msg ->
            val others = msg.tapbacks.filterNot { it.fromMe }
            msg.copy(
                tapbacks = if (added) {
                    others + Tapback(kind, fromMe = true, senderId = me.id, emoji = emoji)
                } else {
                    others
                }
            )
        }
    }

    override suspend fun edit(messageId: String, newText: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val chat = _chats.value.firstOrNull { it.id == message.chatId } ?: return
        updateMessage(messageId) {
            it.copy(
                text = newText,
                editHistory = it.editHistory + it.text,
                editedAt = System.currentTimeMillis(),
            )
        }
        runCatching {
            core.sendEdit(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
                targetId = messageId,
                targetPart = 0uL,
                newText = newText,
            )
        }.onFailure { Log.e(TAG, "edit failed", it) }
    }

    override suspend fun unsend(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        val chat = _chats.value.firstOrNull { it.id == message.chatId } ?: return
        // The text is kept locally for one-tap reveal. It's gone from the
        // other side, which is the point, but hiding it from yourself as well
        // just means you can't remember what you retracted.
        updateMessage(messageId) { it.copy(unsentText = it.text, isUnsent = true, text = "") }
        runCatching {
            core.sendUnsend(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
                targetId = messageId,
                targetPart = 0uL,
            )
        }.onFailure { Log.e(TAG, "unsend failed", it) }
    }

    override suspend fun setTyping(chatId: String, typing: Boolean) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        runCatching {
            core.sendTyping(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
                typing = typing,
            )
        }.onFailure { Log.d(TAG, "typing indicator not sent", it) }
    }

    override suspend fun markRead(chatId: String) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        if (chat.unreadCount == 0) return
        updateChat(chatId) { it.copy(unreadCount = 0) }
        runCatching {
            core.sendRead(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
            )
        }.onFailure { Log.d(TAG, "read receipt not sent", it) }
    }

    override suspend fun markUnread(chatId: String) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        updateChat(chatId) { it.copy(unreadCount = maxOf(it.unreadCount, 1)) }
        runCatching {
            core.sendMarkUnread(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
            )
        }.onFailure { Log.d(TAG, "unread state not synced", it) }
    }

    override suspend fun startChat(handles: List<String>): String {
        val targets = handles.map(Handles::normalize).distinct()
        require(targets.isNotEmpty()) { "No one to message." }

        // No pre-flight gate here, deliberately. This used to refuse to open a
        // chat when the IDS lookup came back empty, which turned every
        // false negative into "that person isn't on iMessage" and made it
        // impossible to message anyone at all. Real Messages lets you address
        // whoever you like and settles delivery afterwards; a lookup is a hint
        // about bubble colour, never a reason to block a conversation.

        // A group needs a GUID minted here; a one-to-one chat is identified by
        // the other participant, so it gets none.
        val guid = if (targets.size > 1) UUID.randomUUID().toString() else null
        val chatId = Handles.chatId(targets, guid)
        _chats.value.firstOrNull { it.id == chatId }?.let { return chatId }

        val participants = targets.map { contactFor(it) }
        mutate { chats, messages ->
            chats + Chat(
                id = chatId,
                displayName = participants.joinToString(", ") { it.displayName },
                participants = participants,
                lastMessage = null,
            ) to messages
        }
        return chatId
    }

    // --- Local-only state ---------------------------------------------------
    //
    // None of these send anything. They're this device's view of the
    // conversation, and iMessage has no message for most of them.

    override suspend fun delete(messageId: String) {
        mutate { chats, messages ->
            val remaining = messages.filterNot { it.id == messageId }
            chats.map { it.withLatest(remaining) } to remaining
        }
    }

    override suspend fun setBookmarked(messageId: String, bookmarked: Boolean) =
        updateMessage(messageId) { it.copy(isBookmarked = bookmarked) }

    override suspend fun setMessagePinned(messageId: String, pinned: Boolean) =
        updateMessage(messageId) { it.copy(isPinned = pinned) }

    override suspend fun setNote(messageId: String, note: String?) =
        updateMessage(messageId) { it.copy(note = note?.takeIf { n -> n.isNotBlank() }) }

    override suspend fun setReminder(messageId: String, at: Long?) =
        updateMessage(messageId) { it.copy(remindAt = at) }

    override suspend fun setPinned(chatId: String, pinned: Boolean) =
        updateChat(chatId) { it.copy(isPinned = pinned) }

    override suspend fun setMuted(chatId: String, muted: Boolean) =
        updateChat(chatId) { it.copy(isMuted = muted) }

    override suspend fun setArchived(chatId: String, archived: Boolean) =
        updateChat(chatId) { it.copy(isArchived = archived, isPinned = false) }

    override suspend fun deleteChat(chatId: String) {
        mutate { chats, messages ->
            chats.filterNot { it.id == chatId } to messages.filterNot { it.chatId == chatId }
        }
    }

    override suspend fun scheduleSend(chatId: String, text: String, at: Long) {
        val id = UUID.randomUUID().toString()
        append(
            Message(
                id = id,
                chatId = chatId,
                text = text,
                timestamp = at,
                isFromMe = true,
                senderId = me.id,
                deliveryState = DeliveryState.SENDING,
                scheduledFor = at,
            )
        )
        armScheduled(id, at)
    }

    /**
     * Waits out a scheduled send.
     *
     * The timer lives only as long as the process, which is why [start] sweeps
     * the store on launch: closing the app used to cancel a scheduled message
     * silently, and it would sit in the transcript marked "sending" forever.
     */
    private fun armScheduled(id: String, at: Long) {
        scope.launch {
            delay((at - System.currentTimeMillis()).coerceAtLeast(0))
            // Only fire if it's still queued - it may have been sent early or
            // cancelled while we were waiting.
            if (_messages.value.any { it.id == id && it.scheduledFor != null }) {
                resolveScheduled(id, send = true)
            }
        }
    }

    override suspend fun resolveScheduled(messageId: String, send: Boolean) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (!send) {
            delete(messageId)
            return
        }
        // Drop the placeholder and send for real, so it goes out through the
        // same path as anything else and picks up a genuine GUID.
        delete(messageId)
        send(message.chatId, message.text, message.effect, message.replyToId)
    }

    override suspend fun sendPoll(chatId: String, question: String, options: List<String>) {
        // iMessage has no poll message. Rendering it as a poll card here and
        // sending readable text keeps it from arriving as an empty bubble on
        // an actual iPhone.
        val poll = Poll(
            question = question,
            options = options.filter { it.isNotBlank() }
                .map { PollOption(UUID.randomUUID().toString(), it.trim()) },
        )
        val body = buildString {
            append(question)
            poll.options.forEachIndexed { i, option -> append("\n${i + 1}. ${option.label}") }
        }
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val localId = UUID.randomUUID().toString()
        append(
            Message(
                id = localId,
                chatId = chatId,
                text = "",
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                senderId = me.id,
                deliveryState = DeliveryState.SENDING,
                poll = poll,
            )
        )
        runCatching {
            core.sendText(
                participants = chat.sendTargets(),
                groupName = chat.groupName(),
                senderGuid = chat.groupGuid(),
                text = body,
                replyToId = null,
                replyToPart = null,
                effect = null,
            )
        }.onSuccess { guid -> replaceId(localId, guid, DeliveryState.SENT) }
            .onFailure { updateMessage(localId) { m -> m.copy(deliveryState = DeliveryState.FAILED) } }
    }

    override suspend fun votePoll(messageId: String, optionId: String) {
        updateMessage(messageId) { msg ->
            val poll = msg.poll ?: return@updateMessage msg
            val already = poll.options.firstOrNull { it.id == optionId }
                ?.voters?.contains(me.id) == true
            msg.copy(
                poll = poll.copy(
                    options = poll.options.map { option ->
                        when {
                            option.id == optionId && already ->
                                option.copy(voters = option.voters - me.id)
                            option.id == optionId ->
                                option.copy(voters = option.voters + me.id)
                            !poll.allowsMultiple ->
                                option.copy(voters = option.voters - me.id)
                            else -> option
                        }
                    }
                )
            )
        }
    }

    // --- Receiving ----------------------------------------------------------

    private inner class Listener : EventListener {
        override fun onEvent(event: IncomingEvent) {
            // The callback comes from a Rust thread; everything below touches
            // the store, so it moves onto our own scope first.
            scope.launch { handle(event) }
        }

        override fun onAudioFrame(frame: ByteArray, timestamp: UInt) {
            // Straight to the queue - this runs on the media thread.
            audio?.onFrame(frame)
        }

        override fun onAudioConfig(config: ByteArray) {
            audio?.onConfig(config)
        }

        override fun onCallEvent(event: uniffi.imessage_core.CallEvent) {
            // Straight through - Calls does its own state handling, and it has
            // to happen in order, so this must not be dispatched onto a
            // coroutine that could reorder two events.
            calls.onEvent(event)
        }

        override fun onStateChanged() {
            scope.launch { store.flush() }
        }

        override fun onConnectionLost(reason: String) {
            Log.w(TAG, "push connection lost: $reason")
        }
    }

    private suspend fun handle(event: IncomingEvent) {
        val fromMe = event.sender != null && event.sender in myHandles
        val people = participantsOf(event)
        val chatId = Handles.chatId(people, event.conversation.senderGuid)

        when (val kind = event.kind) {
            is EventKind.Text -> {
                ensureChat(chatId, event)
                val message = Message(
                    id = event.id,
                    chatId = chatId,
                    text = kind.text,
                    timestamp = event.timestampMs.toLong(),
                    isFromMe = fromMe,
                    senderId = event.sender?.let { Handles.contactId(it) },
                    service = if (kind.isSms) Service.SMS else Service.IMESSAGE,
                    deliveryState = DeliveryState.DELIVERED,
                    effect = effectFrom(kind.effect),
                    replyToId = kind.replyToId,
                    attachments = kind.attachments.map {
                        Attachment(
                            id = UUID.randomUUID().toString(),
                            fileName = it.name,
                            mimeType = it.mimeType,
                            // Attachments arrive as MMCS references; the bytes
                            // are a separate fetch, so there's no file yet.
                            uri = it.localPath.takeIf { path -> path.isNotEmpty() },
                            sizeBytes = it.sizeBytes.toLong(),
                        )
                    },
                )
                val watching = chatId == openChatId
                append(message, incrementUnread = !fromMe && !watching)

                // A message landing clears the typing bubble - the sender has
                // finished typing by definition, and iMessage sends no
                // separate "stopped" for it.
                if (!fromMe) updateChat(chatId) { it.copy(isTyping = false) }

                // Reading it as it arrives should tell them so, the same as
                // opening the thread would.
                if (!fromMe && watching) {
                    scope.launch { runCatching { markRead(chatId) } }
                }

                // Fetch the bytes straight away rather than on tap. An
                // attachment reference is only good while the sender's upload
                // lives on Apple's servers, so "download it when you look at
                // it" means the ones you look at late are gone.
                if (message.attachments.isNotEmpty()) {
                    scope.launch {
                        message.attachments.forEach { attachment ->
                            downloadAttachment(message.id, attachment.id)
                        }
                    }
                }
            }

            is EventKind.Tapback -> updateMessage(kind.targetId) { msg ->
                val others = msg.tapbacks.filterNot { it.fromMe == fromMe }
                if (!kind.added) {
                    msg.copy(tapbacks = others)
                } else {
                    val builtIn = kind.reaction.toTapbackKind()
                    msg.copy(
                        tapbacks = others + Tapback(
                            kind = builtIn ?: TapbackKind.ANY_EMOJI,
                            fromMe = fromMe,
                            senderId = event.sender?.let { Handles.contactId(it) }.orEmpty(),
                            emoji = if (builtIn == null) kind.reaction else null,
                        )
                    )
                }
            }

            is EventKind.Edit -> updateMessage(kind.targetId) {
                it.copy(
                    text = kind.newText,
                    editHistory = it.editHistory + it.text,
                    editedAt = event.timestampMs.toLong(),
                )
            }

            is EventKind.Unsend -> updateMessage(kind.targetId) {
                it.copy(unsentText = it.text.ifEmpty { it.unsentText }, isUnsent = true, text = "")
            }

            is EventKind.Typing -> updateChat(chatId) { it.copy(isTyping = kind.active) }

            // Our messages were read. Only the newest one shows a receipt, so
            // marking them all keeps that consistent as the thread grows.
            EventKind.Read -> markOwnMessages(chatId, DeliveryState.READ)
            EventKind.Delivered -> markOwnMessages(chatId, DeliveryState.DELIVERED)

            EventKind.MarkedUnread -> updateChat(chatId) {
                it.copy(unreadCount = maxOf(it.unreadCount, 1))
            }

            is EventKind.GroupRenamed -> updateChat(chatId) { it.copy(displayName = kind.name) }

            is EventKind.ParticipantsChanged -> updateChat(chatId) { chat ->
                chat.copy(
                    participants = kind.participants
                        .filterNot { it in myHandles }
                        .map { contactFor(it) }
                )
            }

            is EventKind.SendFailed -> updateMessage(kind.targetId) {
                it.copy(deliveryState = DeliveryState.FAILED)
            }

            is EventKind.Unhandled -> Log.d(TAG, "ignoring: ${kind.description}")
        }
    }

    private suspend fun markOwnMessages(chatId: String, state: DeliveryState) {
        mutate { chats, messages ->
            chats to messages.map { msg ->
                // Never walk a receipt backwards: a late "delivered" arriving
                // after a "read" would otherwise un-read the thread.
                if (msg.chatId == chatId && msg.isFromMe && msg.deliveryState < state) {
                    msg.copy(deliveryState = state)
                } else {
                    msg
                }
            }
        }
    }

    /**
     * Who a conversation is with, from a message's participant list.
     *
     * Our own handle is on the wire but isn't a person in the conversation -
     * leaving it in would make every one-to-one chat look like a two-person
     * group. The exception is a message to yourself, which carries only your
     * own handle: filtering there leaves nothing at all, and an empty list
     * produces a conversation key that matches no real thread, so the message
     * lands in a phantom chat and never appears where you're looking.
     */
    private fun participantsOf(event: IncomingEvent): List<String> {
        val everyone = event.conversation.participants.map(Handles::normalize).distinct()
        val others = everyone.filterNot { it in myHandles }
        return others.ifEmpty { everyone.take(1) }
    }

    private suspend fun ensureChat(chatId: String, event: IncomingEvent) {
        if (_chats.value.any { it.id == chatId }) return
        val participants = participantsOf(event).map { contactFor(it) }
        mutate { chats, messages ->
            chats + Chat(
                id = chatId,
                displayName = event.conversation.groupName
                    ?: participants.joinToString(", ") { it.displayName },
                participants = participants,
                lastMessage = null,
            ) to messages
        }
    }

    // --- Store plumbing -----------------------------------------------------

    private suspend fun mutate(
        block: (List<Chat>, List<Message>) -> Pair<List<Chat>, List<Message>>,
    ) {
        store.update(block)
        _chats.value = store.chats
        _messages.value = store.messages
    }

    private suspend fun append(message: Message, incrementUnread: Boolean = false) {
        mutate { chats, messages ->
            // Apple re-delivers a message if the first acknowledgement was
            // lost, so the same GUID can arrive twice.
            if (messages.any { it.id == message.id }) return@mutate chats to messages
            val updated = messages + message
            chats.map { chat ->
                if (chat.id != message.chatId) chat
                else chat.withLatest(updated).let {
                    if (incrementUnread) it.copy(unreadCount = it.unreadCount + 1) else it
                }
            } to updated
        }
    }

    private suspend fun updateMessage(id: String, block: (Message) -> Message) {
        mutate { chats, messages ->
            val updated = messages.map { if (it.id == id) block(it) else it }
            chats.map { it.withLatest(updated) } to updated
        }
    }

    private suspend fun updateChat(id: String, block: (Chat) -> Chat) {
        mutate { chats, messages ->
            chats.map { if (it.id == id) block(it) else it } to messages
        }
    }

    /**
     * Swaps a locally-minted id for the GUID Apple assigned.
     *
     * The collision this guards against is easy to hit and fatal: send a
     * message, and Apple can fan its own copy back to us before this runs.
     * The echo is appended under the real GUID, and renaming the local
     * placeholder to that same GUID then leaves two messages sharing an id -
     * which the transcript keys by, so it crashes outright.
     *
     * The echo is authoritative, so when one is already present the
     * placeholder is dropped rather than renamed. Local-only fields are
     * carried across first, since the copy from Apple has never heard of the
     * bookmark or note you put on it.
     */
    private suspend fun replaceId(oldId: String, newId: String, state: DeliveryState) {
        mutate { chats, messages ->
            val local = messages.firstOrNull { it.id == oldId }
            val existing = messages.firstOrNull { it.id == newId }

            val updated = when {
                local == null -> messages
                existing != null -> messages
                    .filterNot { it.id == oldId }
                    .map {
                        if (it.id != newId) it else it.copy(
                            // Delivery only moves forward, so keep whichever
                            // of the two got further.
                            deliveryState = maxOf(it.deliveryState, state),
                            isBookmarked = it.isBookmarked || local.isBookmarked,
                            isPinned = it.isPinned || local.isPinned,
                            note = it.note ?: local.note,
                            remindAt = it.remindAt ?: local.remindAt,
                            poll = it.poll ?: local.poll,
                            // The echo comes back without the local file URIs,
                            // so a sent photo would otherwise lose its preview.
                            attachments = if (it.attachments.isEmpty()) local.attachments
                            else it.attachments,
                        )
                    }
                else -> messages.map {
                    if (it.id == oldId) it.copy(id = newId, deliveryState = state) else it
                }
            }
            chats.map { it.withLatest(updated) } to updated
        }
    }

    private companion object {
        const val TAG = "RustBackend"
    }
}

// --- Mapping helpers ---------------------------------------------------------

private fun Chat.withLatest(messages: List<Message>): Chat =
    copy(lastMessage = messages.filter { it.chatId == id }.maxByOrNull { it.timestamp })

/** The handles to address a message to. */
private fun Chat.sendTargets(): List<String> = participants.map { Handles.normalize(it.handle) }

/** Only groups carry a name on the wire; sending one for a 1:1 renames nothing. */
private fun Chat.groupName(): String? = if (isGroup) displayName else null

private fun Chat.groupGuid(): String? = id.removePrefix("group:").takeIf { id.startsWith("group:") }

/**
 * iMessage names its effects with reverse-DNS identifiers. Anything the app
 * doesn't recognise sends as a plain message rather than guessing.
 */
private fun MessageEffect.wireName(): String? = when (this) {
    MessageEffect.NONE -> null
    MessageEffect.SLAM -> "com.apple.MobileSMS.expressivesend.impact"
    MessageEffect.LOUD -> "com.apple.MobileSMS.expressivesend.loud"
    MessageEffect.GENTLE -> "com.apple.MobileSMS.expressivesend.gentle"
    MessageEffect.INVISIBLE_INK -> "com.apple.MobileSMS.expressivesend.invisibleink"
}

private fun effectFrom(wire: String?): MessageEffect = when (wire) {
    "com.apple.MobileSMS.expressivesend.impact" -> MessageEffect.SLAM
    "com.apple.MobileSMS.expressivesend.loud" -> MessageEffect.LOUD
    "com.apple.MobileSMS.expressivesend.gentle" -> MessageEffect.GENTLE
    "com.apple.MobileSMS.expressivesend.invisibleink" -> MessageEffect.INVISIBLE_INK
    else -> MessageEffect.NONE
}

private fun TapbackKind.wireName(): String = when (this) {
    TapbackKind.HEART -> "heart"
    TapbackKind.THUMBS_UP -> "like"
    TapbackKind.THUMBS_DOWN -> "dislike"
    TapbackKind.HAHA -> "laugh"
    TapbackKind.EXCLAIM -> "emphasize"
    TapbackKind.QUESTION -> "question"
    // Shouldn't be reached - emoji tapbacks go through setEmojiTapback, which
    // sends the emoji itself.
    TapbackKind.ANY_EMOJI -> "heart"
}

/** Null for anything that isn't one of the six built-ins, i.e. an emoji. */
private fun String.toTapbackKind(): TapbackKind? = when (this) {
    "heart" -> TapbackKind.HEART
    "like" -> TapbackKind.THUMBS_UP
    "dislike" -> TapbackKind.THUMBS_DOWN
    "laugh" -> TapbackKind.HAHA
    "emphasize" -> TapbackKind.EXCLAIM
    "question" -> TapbackKind.QUESTION
    else -> null
}
