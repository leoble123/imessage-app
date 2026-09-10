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

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            messages = root.optJSONArray("messages").map { it.toMessage() }
            chats = root.optJSONArray("chats").map { it.toChat(messages) }
        } catch (e: Exception) {
            // A truncated file (killed mid-write) would otherwise crash on
            // every launch with no way out but clearing app data. Starting
            // empty loses history, which is bad, but recoverable by the
            // messages that arrive next; a crash loop is not.
            Log.e(TAG, "history file unreadable, starting empty", e)
            file.renameTo(File(file.parentFile, file.name + ".corrupt"))
        }
    }

    private suspend fun persist() {
        val snapshot = lock.withLock {
            JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("chats", JSONArray().apply { chats.forEach { put(it.toJson()) } })
                put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
            }.toString()
        }
        try {
            // Write to a sibling and rename: a rename is atomic, so a kill
            // during the write leaves the previous good file intact rather
            // than a half-written one.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(snapshot)
            temp.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "couldn't save history", e)
        }
    }

    private companion object {
        const val TAG = "MessageStore"
        const val FORMAT_VERSION = 1
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
 * edit or an unsend.
 */
internal fun JSONObject.toChat(allMessages: List<Message>): Chat {
    val id = getString("id")
    return Chat(
        id = id,
        displayName = optString("displayName"),
        participants = optJSONArray("participants").map { it.toContact() },
        lastMessage = allMessages.filter { it.chatId == id }.maxByOrNull { it.timestamp },
        unreadCount = optInt("unreadCount"),
        isPinned = optBoolean("isPinned"),
        isMuted = optBoolean("isMuted"),
        isArchived = optBoolean("isArchived"),
        service = runCatching { Service.valueOf(optString("service")) }
            .getOrDefault(Service.IMESSAGE),
    )
}
