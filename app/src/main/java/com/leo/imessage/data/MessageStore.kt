package com.leo.imessage.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
 * That's also why this is written by hand against `org.json` instead of
 * pulling in a serialization framework: the format is something we have to be
 * able to migrate deliberately, and a schema that silently changes shape when
 * a data class gains a field is the wrong property for a file whose contents
 * can't be regenerated. Unknown fields are ignored and missing ones take
 * defaults, so an older file opens in a newer build.
 */
class MessageStore(private val file: File) {

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Coalesces writes. A burst of twenty incoming messages should produce one
     * file write, not twenty, but the write must not be deferred so long that
     * a process kill loses messages - Android can stop the app at any moment.
     */
    private val writeRequests = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    var chats: List<Chat> = emptyList()
        private set

    var messages: List<Message> = emptyList()
        private set

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
        load()
        scope.launch {
            writeRequests.collect {
                // Long enough to absorb a burst, short enough that nothing
                // meaningful is lost if the process dies right after.
                kotlinx.coroutines.delay(400)
                persist()
            }
        }
    }

    /** Applies a change and schedules a save. */
    suspend fun update(block: (List<Chat>, List<Message>) -> Pair<List<Chat>, List<Message>>) {
        lock.withLock {
            val (newChats, newMessages) = block(chats, messages)
            chats = newChats
            messages = newMessages
        }
        writeRequests.tryEmit(Unit)
    }

    /** Forces an immediate write - used when the app is going to the background. */
    suspend fun flush() = persist()

    /**
     * Reads the history back.
     *
     * The file is one JSON object per line rather than a single document:
     * chats on the first line, then one message per line after it. That is the
     * whole reason it can be read at all on a phone - the previous format was
     * one enormous object, which had to exist three times over in memory
     * (bytes, then a UTF-16 string, then a parsed tree) before a single
     * message could be read, and a few years of history exceeded the heap.
     */
    private fun load() {
        if (!file.exists()) return
        try {
            val loadedMessages = ArrayList<Message>()
            var loadedChats: List<Chat> = emptyList()

            file.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    if (index == 0) {
                        val header = JSONObject(line)
                        // Chats are few and small; only the messages need
                        // streaming.
                        loadedChats = header.optJSONArray("chats").map { it.toChatShell() }
                    } else {
                        // A single unreadable line loses one message, not the
                        // entire history.
                        runCatching { JSONObject(line).toMessage() }
                            .onSuccess { loadedMessages.add(it) }
                            .onFailure { Log.w(TAG, "skipping an unreadable message", it) }
                    }
                }
            }

            messages = loadedMessages
            chats = loadedChats.map { chat ->
                chat.copy(
                    lastMessage = loadedMessages
                        .filter { it.chatId == chat.id }
                        .maxByOrNull { it.timestamp }
                )
            }
        } catch (e: Throwable) {
            // The history is the only copy there is - iMessage never resends
            // anything - so it is set aside rather than deleted, and the
            // failure is surfaced instead of the app quietly opening empty as
            // though nothing had happened.
            Log.e(TAG, "history file unreadable", e)
            val salvaged = File(file.parentFile, file.name + ".corrupt")
            runCatching { file.copyTo(salvaged, overwrite = true) }
            loadFailure = "Couldn't read your saved messages. The file was kept as " +
                "${salvaged.name} in case it can be recovered."
        }
    }

    private suspend fun persist() {
        val (chatSnapshot, messageSnapshot) = lock.withLock { chats to messages }
        try {
            // Written to a sibling and renamed. A rename is atomic, so being
            // killed mid-write leaves the previous good file rather than a
            // half-written one.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.bufferedWriter().use { out ->
                out.write(
                    JSONObject().apply {
                        put("version", FORMAT_VERSION)
                        put("chats", JSONArray().apply { chatSnapshot.forEach { put(it.toJson()) } })
                    }.toString()
                )
                out.newLine()
                // One message per line, written as we go. The old format built
                // a single string holding every message at once, which grew
                // without bound and eventually failed during the save itself -
                // the one moment where failing loses the file.
                messageSnapshot.forEach { message ->
                    out.write(message.toJson().toString())
                    out.newLine()
                }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "couldn't save history", e)
        }
    }

    private companion object {
        const val TAG = "MessageStore"
        const val FORMAT_VERSION = 2
    }
}

// --- Serialization -----------------------------------------------------------
//
// Everything below is the file format. Each reader takes a default so that a
// file written by an older build still opens.

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

private fun Contact.toJson() = JSONObject().apply {
    put("id", id)
    put("displayName", displayName)
    put("handle", handle)
}

private fun JSONObject.toContact() = Contact(
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

internal fun Chat.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("displayName", displayName)
    put("participants", JSONArray().apply { participants.forEach { put(it.toJson()) } })
    put("unreadCount", unreadCount)
    put("isPinned", isPinned)
    put("isMuted", isMuted)
    put("isArchived", isArchived)
    put("service", service.name)
}

/**
 * `lastMessage` isn't stored - it's the newest message for this chat, and
 * writing it twice invites the copy and the transcript disagreeing after an
 * edit or an unsend. The loader recomputes it.
 */
internal fun JSONObject.toChatShell(): Chat {
    val id = getString("id")
    return Chat(
        id = id,
        displayName = optString("displayName"),
        participants = optJSONArray("participants").map { it.toContact() },
        // Filled in by the loader once the messages have been read.
        lastMessage = null,
        unreadCount = optInt("unreadCount"),
        isPinned = optBoolean("isPinned"),
        isMuted = optBoolean("isMuted"),
        isArchived = optBoolean("isArchived"),
        service = runCatching { Service.valueOf(optString("service")) }
            .getOrDefault(Service.IMESSAGE),
    )
}
