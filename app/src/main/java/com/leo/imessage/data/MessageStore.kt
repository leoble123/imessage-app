package com.leo.imessage.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * A change to the store, for anything watching a conversation.
 *
 * [chatId] narrows it: a message arriving in one thread should not make every
 * other open transcript re-run its query. Null means "assume everything moved"
 * - an import, a reload, a bulk delete.
 */
data class StoreChange(val serial: Long, val chatId: String?)

/**
 * On-disk conversation history.
 *
 * iMessage itself has none to give: it's a live protocol, and Apple's servers
 * hand a newly registered device the messages that arrive *from now on*, not
 * what came before. Every client that shows a scrollback - Messages.app
 * included - is showing its own local copy. So this is not a cache that can be
 * rebuilt by refetching; it is the only record, and losing it loses the
 * conversation.
 *
 * The messages live in SQLite ([MessageDatabase]) and are read a page at a
 * time. The conversation list is small and is kept in memory, because it is
 * what the first screen draws and it changes on every message.
 *
 * This class is the only thing that writes. Everything that mutates history
 * goes through one mutex, which is what makes a read-modify-write - adopting a
 * GUID, applying a tapback - atomic against a message arriving at the same
 * moment on the push thread.
 */
class MessageStore(context: Context, private val legacyFile: File) {

    private val db = MessageDatabase(context)
    private val lock = Mutex()
    private val serial = AtomicLong(0)

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())

    /** The conversation list. Cheap to collect - it is already in memory. */
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _changes = MutableStateFlow(StoreChange(0, null))

    /** Bumped whenever messages change, so open transcripts re-query. */
    val changes: StateFlow<StoreChange> = _changes.asStateFlow()

    /**
     * Set when the history could not be read.
     *
     * Surfaced in Settings rather than only logged: every message vanishing
     * with no explanation is the single most alarming thing this app could do.
     */
    @Volatile
    var loadFailure: String? = null
        private set

    init {
        runCatching { migrateLegacyFile() }
            .onFailure { Log.e(TAG, "couldn't migrate the old history file", it) }
        _chats.value = runCatching { db.chats() }
            .onFailure {
                Log.e(TAG, "couldn't open the message database", it)
                loadFailure = "Couldn't open your saved messages: ${it.message}"
            }
            .getOrDefault(emptyList())
    }

    // --- Reads ----------------------------------------------------------------
    //
    // Not suspending, and not behind the lock. SQLite serializes readers
    // against writers itself, and with write-ahead logging a read never waits
    // on one - so making the UI's queries suspend would buy nothing but a
    // frame of latency.

    fun chatsNow(): List<Chat> = _chats.value

    fun chat(id: String): Chat? = _chats.value.firstOrNull { it.id == id }

    fun messages(chatId: String, limit: Int = FULL_PAGE): List<Message> =
        read("messages", emptyList()) { db.messages(chatId, limit) }

    fun message(id: String): Message? = read("message", null) { db.message(id) }

    fun hasMessage(id: String): Boolean = read("hasMessage", false) { db.hasMessage(id) }

    /** Everything still queued to send later, for re-arming after a restart. */
    fun scheduledMessages(): List<Message> = read("scheduled", emptyList()) { db.scheduled() }

    /** Every attachment URI still pointed at, for pruning orphaned files. */
    fun referencedAttachmentUris(): Set<String> =
        read("attachments", emptySet()) { db.referencedAttachmentUris() }

    /**
     * A failed query returns the empty answer rather than taking the app down.
     *
     * A corrupt database should mean an empty thread and a note in Settings,
     * not a crash on launch that leaves no way in to sign out or re-import.
     */
    private inline fun <T> read(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            Log.e(TAG, "read failed ($what)", e)
            loadFailure = "Couldn't read your saved messages: ${e.message}"
            fallback
        }

    // --- Message writes -------------------------------------------------------

    /**
     * Adds a message, unless one with that id is already here.
     *
     * Returns false when it was a duplicate. Apple re-delivers a message if
     * the first acknowledgement was lost, so the same GUID genuinely does
     * arrive twice.
     */
    suspend fun insert(message: Message, incrementUnread: Boolean = false): Boolean =
        lock.withLock {
            if (db.hasMessage(message.id)) return@withLock false
            db.upsertMessage(message)
            if (incrementUnread) {
                db.chat(message.chatId)?.let { db.upsertChat(it.copy(unreadCount = it.unreadCount + 1)) }
            }
            refreshChat(message.chatId)
            bump(message.chatId)
            true
        }

    /** Rewrites one message in place. Does nothing if it isn't here. */
    suspend fun updateMessage(id: String, block: (Message) -> Message): Message? =
        lock.withLock {
            val existing = db.message(id) ?: return@withLock null
            val updated = block(existing)
            db.upsertMessage(updated)
            refreshChat(updated.chatId)
            if (updated.chatId != existing.chatId) refreshChat(existing.chatId)
            bump(updated.chatId)
            updated
        }

    /**
     * Applies a change to our own recent messages in one conversation.
     *
     * [block] returns null to leave a message alone, which is what keeps a
     * read receipt from rewriting - and re-saving - a thread that was already
     * marked read.
     */
    suspend fun updateOwnMessages(chatId: String, block: (Message) -> Message?) = lock.withLock {
        val changed = db.ownMessages(chatId).mapNotNull(block)
        if (changed.isEmpty()) return@withLock
        db.upsertMessages(changed)
        refreshChat(chatId)
        bump(chatId)
    }

    suspend fun deleteMessage(id: String) = lock.withLock {
        val existing = db.message(id) ?: return@withLock
        db.deleteMessage(id)
        refreshChat(existing.chatId)
        bump(existing.chatId)
    }

    /**
     * Swaps a locally-minted id for the GUID Apple assigned.
     *
     * The collision this guards against is easy to hit and fatal: send a
     * message, and Apple can fan its own copy back to us before this runs.
     * The echo is stored under the real GUID, and renaming the local
     * placeholder to that same GUID would then leave two messages sharing an
     * id - which the transcript keys by, so it crashes outright.
     *
     * The echo is authoritative, so when one is already present the
     * placeholder is dropped rather than renamed, and only the local-only
     * fields are carried across: the copy from Apple has never heard of the
     * bookmark or the note you put on it.
     */
    suspend fun adoptGuid(localId: String, guid: String, state: DeliveryState) = lock.withLock {
        if (localId == guid) return@withLock
        val local = db.message(localId) ?: return@withLock
        val echo = db.message(guid)

        val merged = if (echo == null) {
            local.copy(id = guid, deliveryState = state, scheduledFor = null)
        } else {
            echo.copy(
                // Delivery only moves forward, so keep whichever got further.
                deliveryState = maxOf(echo.deliveryState, state),
                isBookmarked = echo.isBookmarked || local.isBookmarked,
                isPinned = echo.isPinned || local.isPinned,
                note = echo.note ?: local.note,
                remindAt = echo.remindAt ?: local.remindAt,
                poll = echo.poll ?: local.poll,
                // The echo comes back without the local file URIs, so a sent
                // photo would otherwise lose its preview.
                attachments = echo.attachments.ifEmpty { local.attachments },
            )
        }
        db.deleteMessage(localId)
        db.upsertMessage(merged)
        refreshChat(merged.chatId)
        bump(merged.chatId)
    }

    // --- Chat writes ----------------------------------------------------------

    /** Adds a conversation if it isn't already here, and returns it either way. */
    suspend fun addChat(chat: Chat): Chat = lock.withLock {
        _chats.value.firstOrNull { it.id == chat.id }?.let { return@withLock it }
        db.upsertChat(chat)
        refreshChat(chat.id)
        _chats.value.firstOrNull { it.id == chat.id } ?: chat
    }

    suspend fun updateChat(id: String, block: (Chat) -> Chat) = lock.withLock {
        val existing = _chats.value.firstOrNull { it.id == id } ?: return@withLock
        val updated = block(existing)
        db.upsertChat(updated)
        // Straight into the cache rather than re-reading: `isTyping` is a live
        // signal that is deliberately never written to disk, so a round trip
        // through the database would drop it the moment it was set.
        _chats.value = _chats.value.map { if (it.id == id) updated else it }
    }

    suspend fun updateAllChats(block: (Chat) -> Chat) = lock.withLock {
        val updated = _chats.value.map(block)
        db.upsertChats(updated)
        _chats.value = updated
    }

    suspend fun deleteChat(id: String) = lock.withLock {
        db.deleteChat(id)
        _chats.value = _chats.value.filterNot { it.id == id }
        bump(null)
    }

    /**
     * Drops conversations with nobody in them.
     *
     * They could neither send nor receive - the product of a bug that filtered
     * your own handle out of a message addressed only to yourself - and one
     * sitting in the list looks like a real thread that has simply stopped
     * working. Returns how many went.
     */
    suspend fun removeParticipantlessChats(): Int = lock.withLock {
        val broken = db.participantlessChatIds()
        if (broken.isEmpty()) return@withLock 0
        Log.i(TAG, "removing ${broken.size} conversation(s) with no participants")
        db.deleteChats(broken)
        _chats.value = _chats.value.filterNot { it.id in broken }
        bump(null)
        broken.size
    }

    // --- Bulk ------------------------------------------------------------------

    /**
     * Merges an import. One transaction, so a failure part-way through leaves
     * the history as it was rather than half a conversation.
     */
    suspend fun importInto(chats: List<Chat>, messages: List<Message>) = lock.withLock {
        if (messages.isNotEmpty()) db.upsertMessages(messages)
        if (chats.isNotEmpty()) db.upsertChats(chats)
        reloadLocked()
    }

    /** Re-reads everything after something outside this class changed it. */
    suspend fun reload() = lock.withLock { reloadLocked() }

    private fun reloadLocked() {
        _chats.value = runCatching { db.chats() }.getOrDefault(_chats.value)
        bump(null)
    }

    /**
     * Kept for the app-going-to-background path.
     *
     * There is nothing left to write - SQLite committed each change as it was
     * made - so this only folds the write-ahead log back into the main file,
     * which keeps a process kill from leaving a large one behind.
     */
    suspend fun flush() = lock.withLock {
        runCatching { db.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
            .onFailure { Log.w(TAG, "couldn't checkpoint", it) }
        Unit
    }

    // --- Internals -------------------------------------------------------------

    /** Re-reads one conversation's row and its newest message into the cache. */
    private fun refreshChat(chatId: String) {
        val fresh = db.chat(chatId)
        val current = _chats.value
        _chats.value = when {
            fresh == null -> current.filterNot { it.id == chatId }
            current.none { it.id == chatId } -> current + fresh
            // `isTyping` is never persisted, so it has to survive the re-read.
            else -> current.map {
                if (it.id == chatId) fresh.copy(isTyping = it.isTyping) else it
            }
        }
    }

    private fun bump(chatId: String?) {
        _changes.value = StoreChange(serial.incrementAndGet(), chatId)
    }

    /**
     * Moves the old line-per-message file into the database, once.
     *
     * The file is not deleted, it is renamed aside: this is the only copy of
     * the conversation that exists anywhere, and a migration bug found a week
     * later should still be recoverable.
     */
    private fun migrateLegacyFile() {
        if (!legacyFile.exists()) return
        if (db.messageCount() > 0 || db.chatCount() > 0) {
            // Already migrated; the rename must have failed last time.
            setAside()
            return
        }

        Log.i(TAG, "migrating ${legacyFile.name} into the database")
        var migrated = 0
        val batch = ArrayList<Message>(MIGRATE_BATCH)
        try {
            legacyFile.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    if (index == 0) {
                        val header = JSONObject(line)
                        db.upsertChats(header.optJSONArray("chats").map { it.toChatShell() })
                        // Version 1 of the format put everything in this one
                        // object. Reading it here means a file that old still
                        // opens instead of arriving as an empty app.
                        header.optJSONArray("messages")?.let { legacy ->
                            for (i in 0 until legacy.length()) {
                                runCatching { legacy.getJSONObject(i).toMessage() }
                                    .onSuccess { batch += it }
                            }
                        }
                    } else {
                        // A single unreadable line loses one message, not the
                        // entire history.
                        runCatching { JSONObject(line).toMessage() }
                            .onSuccess { batch += it }
                            .onFailure { Log.w(TAG, "skipping an unreadable message", it) }
                    }
                    if (batch.size >= MIGRATE_BATCH) {
                        db.upsertMessages(batch)
                        migrated += batch.size
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) {
                db.upsertMessages(batch)
                migrated += batch.size
            }
            Log.i(TAG, "migrated $migrated message(s)")
            setAside()
        } catch (e: Throwable) {
            Log.e(TAG, "history file unreadable", e)
            loadFailure = "Couldn't read your saved messages. The old file was kept as " +
                "${legacyFile.name}.migrated in case it can be recovered."
            runCatching { setAside() }
        }
    }

    private fun setAside() {
        val kept = File(legacyFile.parentFile, legacyFile.name + ".migrated")
        if (!legacyFile.renameTo(kept)) {
            runCatching {
                legacyFile.copyTo(kept, overwrite = true)
                legacyFile.delete()
            }
        }
    }

    companion object {
        private const val TAG = "MessageStore"

        /** A whole conversation's worth of scrollback. */
        const val FULL_PAGE = 1000

        /**
         * How much is read for the first frame of a transcript.
         *
         * That read happens on the main thread, because a conversation has to
         * be on screen the instant it is tapped - so it is deliberately about
         * a screenful of scrollback rather than the whole page. The full page
         * follows a few milliseconds later, off the main thread, from the
         * flow.
         */
        const val FIRST_PAINT = 150

        /** Rows per transaction while migrating. Bounds peak memory. */
        private const val MIGRATE_BATCH = 500
    }
}

// --- Serialization -----------------------------------------------------------
//
// The message body format, shared by the database's JSON column and by the
// migration that reads the file it replaced. Each reader takes a default so
// that something written by an older build still opens.

private inline fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        runCatching { transform(getJSONObject(i)) }.getOrNull()
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { getString(it) }
}

internal fun Contact.toContactJson() = JSONObject().apply {
    put("id", id)
    put("displayName", displayName)
    put("handle", handle)
}

internal fun JSONObject.toContact() = Contact(
    id = getString("id"),
    displayName = getString("displayName"),
    handle = getString("handle"),
)

private fun Attachment.toJson() = JSONObject().apply {
    put("id", id)
    put("fileName", fileName)
    put("mimeType", mimeType)
    put("uri", uri)
    put("durationMs", durationMs)
    put("sizeBytes", sizeBytes)
    put("transcript", transcript)
}

private fun JSONObject.toAttachment() = Attachment(
    id = getString("id"),
    fileName = getString("fileName"),
    mimeType = optString("mimeType", "application/octet-stream"),
    uri = optStringOrNull("uri"),
    durationMs = optLongOrNull("durationMs"),
    sizeBytes = optLongOrNull("sizeBytes"),
    transcript = optStringOrNull("transcript"),
)

private fun Tapback.toJson() = JSONObject().apply {
    put("kind", kind.name)
    put("fromMe", fromMe)
    put("senderId", senderId)
    put("emoji", emoji)
}

private fun JSONObject.toTapback() = Tapback(
    kind = runCatching { TapbackKind.valueOf(getString("kind")) }
        .getOrDefault(TapbackKind.HEART),
    fromMe = optBoolean("fromMe"),
    senderId = optString("senderId"),
    emoji = optStringOrNull("emoji"),
)

private fun PollOption.toJson() = JSONObject().apply {
    put("id", id)
    put("label", label)
    put("voters", JSONArray(voters))
}

private fun JSONObject.toPollOption() = PollOption(
    id = getString("id"),
    label = optString("label"),
    voters = optJSONArray("voters").strings(),
)

private fun Poll.toJson() = JSONObject().apply {
    put("question", question)
    put("options", JSONArray().apply { options.forEach { put(it.toJson()) } })
    put("allowsMultiple", allowsMultiple)
    put("closesAt", closesAt)
}

private fun JSONObject.toPoll() = Poll(
    question = optString("question"),
    options = optJSONArray("options").map { it.toPollOption() },
    allowsMultiple = optBoolean("allowsMultiple"),
    closesAt = optLongOrNull("closesAt"),
)

internal fun Message.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("chatId", chatId)
    put("text", text)
    put("timestamp", timestamp)
    put("isFromMe", isFromMe)
    put("senderId", senderId)
    put("service", service.name)
    put("deliveryState", deliveryState.name)
    put("effect", effect.name)
    put("replyToId", replyToId)
    put("unsentText", unsentText)
    put("editedAt", editedAt)
    put("isBookmarked", isBookmarked)
    put("isPinned", isPinned)
    put("note", note)
    put("remindAt", remindAt)
    put("scheduledFor", scheduledFor)
    put("failureReason", failureReason)
    put("textPart", textPart)
    put("editHistory", JSONArray(editHistory))
    put("tapbacks", JSONArray().apply { tapbacks.forEach { put(it.toJson()) } })
    put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
    poll?.let { put("poll", it.toJson()) }
}

internal fun JSONObject.toMessage(): Message {
    val unsent = optStringOrNull("unsentText")
    return Message(
        id = getString("id"),
        chatId = getString("chatId"),
        text = optString("text"),
        timestamp = optLong("timestamp"),
        isFromMe = optBoolean("isFromMe"),
        senderId = optStringOrNull("senderId"),
        service = runCatching { Service.valueOf(optString("service")) }
            .getOrDefault(Service.IMESSAGE),
        deliveryState = runCatching { DeliveryState.valueOf(optString("deliveryState")) }
            .getOrDefault(DeliveryState.DELIVERED),
        effect = runCatching { MessageEffect.valueOf(optString("effect")) }
            .getOrDefault(MessageEffect.NONE),
        replyToId = optStringOrNull("replyToId"),
        unsentText = unsent,
        isUnsent = unsent != null,
        editedAt = optLongOrNull("editedAt"),
        isBookmarked = optBoolean("isBookmarked"),
        isPinned = optBoolean("isPinned"),
        note = optStringOrNull("note"),
        remindAt = optLongOrNull("remindAt"),
        scheduledFor = optLongOrNull("scheduledFor"),
        failureReason = optStringOrNull("failureReason"),
        textPart = optLong("textPart"),
        editHistory = optJSONArray("editHistory").strings(),
        tapbacks = optJSONArray("tapbacks").map { it.toTapback() },
        attachments = optJSONArray("attachments").map { it.toAttachment() },
        poll = optJSONObject("poll")?.toPoll(),
    )
}

/**
 * `lastMessage` isn't stored - it's the newest message for this chat, and
 * writing it twice invites the copy and the transcript disagreeing after an
 * edit or an unsend. It is derived on read instead.
 */
internal fun JSONObject.toChatShell(): Chat = Chat(
    id = getString("id"),
    displayName = optString("displayName"),
    participants = optJSONArray("participants").map { it.toContact() },
    lastMessage = null,
    unreadCount = optInt("unreadCount"),
    isPinned = optBoolean("isPinned"),
    isMuted = optBoolean("isMuted"),
    isArchived = optBoolean("isArchived"),
    service = runCatching { Service.valueOf(optString("service")) }
        .getOrDefault(Service.IMESSAGE),
)
