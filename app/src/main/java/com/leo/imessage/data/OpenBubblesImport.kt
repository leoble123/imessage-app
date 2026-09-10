package com.leo.imessage.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Reads a BlueBubbles/OpenBubbles message export.
 *
 * This exists because iMessage never backfills: Apple hands a newly registered
 * device the messages that arrive from now on and nothing that came before, so
 * the only copy of old conversations is whatever the previous client wrote to
 * its own database. OpenBubbles can dump that, and this reads the dump.
 *
 * The file format is the exporter's own, not JSON end to end:
 *
 *     [4 bytes, big endian] length of the JSON block
 *     [that many bytes]     UTF-8 JSON: { chats, messages, atts }
 *     then, repeatedly:
 *     [4 bytes, big endian] length of one attachment
 *     [that many bytes]     its raw contents
 *
 * Attachments appear in the order of the `atts` entries that carry a
 * `bytes_id`; entries without one had no file on disk when the export ran, so
 * their metadata is present and their bytes are not.
 *
 * It is read as a stream rather than into memory. An export with photos in it
 * runs to hundreds of megabytes, and reading that into a byte array to parse
 * is a reliable way to be killed by the OS on a phone.
 */
object OpenBubblesImport {

    data class Result(
        val chats: Int,
        val messages: Int,
        val attachments: Int,
        /** Messages skipped because they were already present. */
        val duplicates: Int,
    )

    /**
     * Parses [uri] and merges it into [store].
     *
     * Merging is by message GUID. Apple's GUIDs are the same ones the live
     * connection uses, so an imported thread and its continuation converge on
     * one conversation instead of sitting side by side, and importing the same
     * file twice changes nothing.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        store: MessageStore,
        contacts: Contacts?,
        myHandles: List<String>,
    ): Result = withContext(Dispatchers.IO) {
        val attachmentDir = File(context.filesDir, "imported").apply { mkdirs() }

        context.contentResolver.openInputStream(uri).use { raw ->
            val input = BufferedInputStream(
                raw ?: throw IllegalArgumentException("Couldn't open that file."),
                64 * 1024,
            )

            val jsonLength = input.readBigEndianInt()
                ?: throw IllegalArgumentException("That file is empty.")
            // A sane upper bound. Without it, a file that isn't an export at
            // all asks for a multi-gigabyte allocation on the first four bytes.
            require(jsonLength in 1..(256 * 1024 * 1024)) {
                "That doesn't look like an OpenBubbles export."
            }

            // Parsed straight off the stream rather than into a string first.
            //
            // The previous version read the whole JSON block into a byte
            // array, decoded it into a String (UTF-16, so twice the size) and
            // handed that to JSONObject, which built a full object tree on top
            // - three copies of a file that can run to tens of megabytes. On a
            // phone that exhausts the heap, and because runCatching also
            // catches Error, the OutOfMemoryError was being reported as "that
            // file isn't a readable export", which sent the search in entirely
            // the wrong direction.
            val parsed = try {
                val reader = android.util.JsonReader(
                    java.io.InputStreamReader(
                        BoundedInputStream(input, jsonLength.toLong()),
                        Charsets.UTF_8,
                    )
                )
                parseStreaming(reader, myHandles, contacts)
            } catch (e: OutOfMemoryError) {
                throw IllegalArgumentException(
                    "That export is too large to import in one go on this phone."
                )
            } catch (e: Exception) {
                // The real reason, not a guess at it.
                throw IllegalArgumentException(
                    "Couldn't read that export: ${e::class.java.simpleName}: ${e.message}"
                )
            }

            // Attachment bytes follow the JSON, in `bytes_id` order.
            val written = writeAttachments(input, parsed.pendingBytes, attachmentDir)

            val merged = merge(store, parsed, written)
            Log.i(TAG, "imported ${merged.chats} chats, ${merged.messages} messages")
            merged
        }
    }

    // --- Parsing ------------------------------------------------------------

    private class Parsed(
        val chats: List<Chat>,
        val messages: List<Message>,
        /** attachment id -> position in the appended byte blocks */
        val pendingBytes: List<PendingAttachment>,
    )

    class PendingAttachment(
        val bytesId: Int,
        val attachmentId: String,
        val fileName: String,
    )

    /**
     * Walks the export a value at a time.
     *
     * The three arrays can appear in any order, and messages reference chats,
     * so the whole thing is read into intermediate lists of small objects
     * rather than resolved on the fly. What this avoids is the *document*
     * ever existing in memory as one piece - each element is parsed, converted
     * and released before the next is read.
     */
    private fun parseStreaming(
        reader: android.util.JsonReader,
        myHandles: List<String>,
        contacts: Contacts?,
    ): Parsed {
        val chatObjects = ArrayList<JSONObject>()
        val messageObjects = ArrayList<JSONObject>()
        val attachmentObjects = ArrayList<JSONObject>()

        reader.isLenient = true
        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "chats" -> readArrayInto(reader, chatObjects)
                "messages" -> readArrayInto(reader, messageObjects)
                "atts" -> readArrayInto(reader, attachmentObjects)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val root = JSONObject().apply {
            put("chats", JSONArray().apply { chatObjects.forEach { put(it) } })
            put("messages", JSONArray().apply { messageObjects.forEach { put(it) } })
            put("atts", JSONArray().apply { attachmentObjects.forEach { put(it) } })
        }
        return parse(root, myHandles, contacts)
    }

    private fun readArrayInto(reader: android.util.JsonReader, into: MutableList<JSONObject>) {
        reader.beginArray()
        while (reader.hasNext()) {
            into.add(readObject(reader))
        }
        reader.endArray()
    }

    /** One JSON value, built as a small object rather than a slice of a huge one. */
    private fun readObject(reader: android.util.JsonReader): JSONObject {
        val out = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (reader.peek()) {
                android.util.JsonToken.NULL -> { reader.nextNull(); out.put(key, JSONObject.NULL) }
                android.util.JsonToken.BOOLEAN -> out.put(key, reader.nextBoolean())
                android.util.JsonToken.NUMBER -> {
                    // Kept as a string and re-read by the callers, which use
                    // optLong/optInt - going through Double would round the
                    // millisecond timestamps.
                    out.put(key, reader.nextString())
                }
                android.util.JsonToken.STRING -> out.put(key, reader.nextString())
                android.util.JsonToken.BEGIN_OBJECT -> out.put(key, readObject(reader))
                android.util.JsonToken.BEGIN_ARRAY -> {
                    val array = JSONArray()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        when (reader.peek()) {
                            android.util.JsonToken.BEGIN_OBJECT -> array.put(readObject(reader))
                            android.util.JsonToken.STRING -> array.put(reader.nextString())
                            android.util.JsonToken.NUMBER -> array.put(reader.nextString())
                            android.util.JsonToken.BOOLEAN -> array.put(reader.nextBoolean())
                            android.util.JsonToken.NULL -> { reader.nextNull() }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endArray()
                    out.put(key, array)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return out
    }

    private fun parse(root: JSONObject, myHandles: List<String>, contacts: Contacts?): Parsed {
        val chatsJson = root.optJSONArray("chats") ?: JSONArray()
        val messagesJson = root.optJSONArray("messages") ?: JSONArray()
        val attsJson = root.optJSONArray("atts") ?: JSONArray()

        // guid -> bytes_id, so a message's attachments can find their bytes.
        val bytesByGuid = HashMap<String, Int>()
        for (i in 0 until attsJson.length()) {
            val att = attsJson.optJSONObject(i) ?: continue
            val guid = att.optString("guid").takeIf { it.isNotEmpty() } ?: continue
            if (!att.isNull("bytes_id")) bytesByGuid[guid] = att.optInt("bytes_id")
        }

        // OpenBubbles' chat guids look like "iMessage;-;+15551234567" for a
        // one-to-one and "iMessage;+;chat123..." for a group. Ours are derived
        // from participants instead, so the two have to be reconciled here or
        // an imported thread sits beside its live twin.
        val chatIdByGuid = HashMap<String, String>()
        val chats = ArrayList<Chat>()

        for (i in 0 until chatsJson.length()) {
            val c = chatsJson.optJSONObject(i) ?: continue
            val guid = c.optString("guid").takeIf { it.isNotEmpty() } ?: continue
            val participants = (c.optJSONArray("participants") ?: JSONArray())
                .let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
                .mapNotNull { it.optString("address").takeIf(String::isNotEmpty) }
                .map(Handles::normalize)
                .filterNot { it in myHandles }
                .distinct()

            if (participants.isEmpty()) continue

            val isGroup = participants.size > 1
            val chatId = Handles.chatId(participants, if (isGroup) guid else null)
            chatIdByGuid[guid] = chatId

            val people = participants.map { Handles.contact(it, contacts?.nameFor(it)) }
            val name = c.optString("displayName").takeIf { it.isNotEmpty() && isGroup }
                ?: people.joinToString(", ") { it.displayName }

            chats += Chat(
                id = chatId,
                displayName = name,
                participants = people,
                lastMessage = null,
                isPinned = c.optBoolean("isPinned"),
                isArchived = c.optBoolean("isArchived"),
                // muteType is a string like "mute"; anything set means muted.
                isMuted = c.optString("muteType").isNotEmpty() &&
                    c.optString("muteType") != "null",
            )
        }

        // Tapbacks and replies are separate rows in the export that point at
        // another message, so the plain messages are built first and the
        // pointers applied afterwards.
        val plain = LinkedHashMap<String, Message>()
        val tapbacks = ArrayList<Triple<String, String, JSONObject>>()
        val pending = ArrayList<PendingAttachment>()

        for (i in 0 until messagesJson.length()) {
            val m = messagesJson.optJSONObject(i) ?: continue
            val guid = m.optString("guid").takeIf { it.isNotEmpty() } ?: continue
            // A message whose conversation isn't in the export used to be
            // dropped without a word. Its participants are on the message, so
            // the conversation can be reconstructed instead of losing it.
            val chatGuid = m.optString("chat")
            val chatId = chatIdByGuid[chatGuid] ?: run {
                val address = m.optJSONObject("handle")?.optString("address")
                    ?.takeIf(String::isNotEmpty)
                    ?.let(Handles::normalize)
                    ?: return@run null
                val recovered = Handles.chatId(listOf(address), null)
                if (chats.none { it.id == recovered }) {
                    val person = Handles.contact(address, contacts?.nameFor(address))
                    chats += Chat(
                        id = recovered,
                        displayName = person.displayName,
                        participants = listOf(person),
                        lastMessage = null,
                    )
                }
                chatIdByGuid[chatGuid] = recovered
                recovered
            } ?: continue

            val associated = m.optString("associatedMessageGuid").takeIf { it.isNotEmpty() }
            val kind = m.optString("associatedMessageType").takeIf { it.isNotEmpty() }
            if (associated != null && kind != null && kind != "null") {
                // "p:0/GUID" - the part index prefix isn't ours to care about.
                tapbacks += Triple(associated.substringAfter('/'), kind, m)
                continue
            }

            val attachments = ArrayList<Attachment>()
            val attArray = m.optJSONArray("attachments") ?: JSONArray()
            for (a in 0 until attArray.length()) {
                val att = attArray.optJSONObject(a) ?: continue
                val attGuid = att.optString("guid").takeIf { it.isNotEmpty() }
                    ?: UUID.randomUUID().toString()
                val fileName = att.optString("transferName").ifEmpty { "attachment" }
                val id = "imported:$attGuid"
                bytesByGuid[attGuid]?.let {
                    pending += PendingAttachment(it, id, fileName)
                }
                attachments += Attachment(
                    id = id,
                    fileName = fileName,
                    mimeType = att.optString("mimeType").ifEmpty { "application/octet-stream" },
                    // Filled in once the bytes are written out.
                    uri = null,
                    sizeBytes = att.optLong("totalBytes").takeIf { it > 0 },
                )
            }

            val fromMe = m.optBoolean("isFromMe")
            val sender = m.optJSONObject("handle")?.optString("address")
                ?.takeIf(String::isNotEmpty)?.let(Handles::normalize)

            val deleted = !m.isNull("dateDeleted")
            // A subject is a separate field on the wire but there's nowhere
            // to show one, so it leads the body rather than being lost.
            val body = m.optString("text").takeIf { it != "null" }.orEmpty()
            val subject = m.optString("subject").takeIf { it.isNotEmpty() && it != "null" }
            val text = if (subject != null) "$subject\n$body" else body

            plain[guid] = Message(
                id = guid,
                chatId = chatId,
                text = if (deleted) "" else text,
                timestamp = m.optLong("dateCreated"),
                isFromMe = fromMe,
                senderId = if (fromMe) "me" else sender?.let(Handles::contactId),
                service = if (m.optString("sendingServiceId") == "SMS") Service.SMS
                    else Service.IMESSAGE,
                deliveryState = when {
                    !m.isNull("dateRead") -> DeliveryState.READ
                    !m.isNull("dateDelivered") || m.optBoolean("isDelivered") ->
                        DeliveryState.DELIVERED
                    fromMe -> DeliveryState.SENT
                    else -> DeliveryState.DELIVERED
                },
                attachments = attachments,
                effect = effectFor(m.optString("expressiveSendStyleId")),
                replyToId = m.optString("threadOriginatorGuid")
                    .takeIf { it.isNotEmpty() && it != "null" }
                    ?.substringAfter('/'),
                unsentText = if (deleted) text.takeIf { it.isNotEmpty() } else null,
                isUnsent = deleted,
                editedAt = m.optLong("dateEdited").takeIf { it > 0 },
                isBookmarked = m.optBoolean("isBookmarked"),
            )
        }

        // Fold the tapback rows onto the messages they point at.
        for ((targetGuid, kind, row) in tapbacks) {
            val target = plain[targetGuid] ?: continue
            val removed = kind.startsWith("-")
            if (removed) continue
            val fromMe = row.optBoolean("isFromMe")
            val sender = row.optJSONObject("handle")?.optString("address").orEmpty()
            val emoji = row.optString("associatedMessageEmoji")
                .takeIf { it.isNotEmpty() && it != "null" }
            plain[targetGuid] = target.copy(
                tapbacks = target.tapbacks + Tapback(
                    kind = tapbackKind(kind, emoji),
                    fromMe = fromMe,
                    senderId = if (fromMe) "me" else Handles.contactId(sender),
                    emoji = emoji,
                )
            )
        }

        return Parsed(chats, plain.values.toList(), pending)
    }

    /** OpenBubbles stores these as words, not the numbers Apple uses. */
    private fun tapbackKind(kind: String, emoji: String?): TapbackKind = when {
        emoji != null -> TapbackKind.ANY_EMOJI
        kind == "love" -> TapbackKind.HEART
        kind == "like" -> TapbackKind.THUMBS_UP
        kind == "dislike" -> TapbackKind.THUMBS_DOWN
        kind == "laugh" -> TapbackKind.HAHA
        kind == "emphasize" -> TapbackKind.EXCLAIM
        kind == "question" -> TapbackKind.QUESTION
        else -> TapbackKind.HEART
    }

    private fun effectFor(style: String): MessageEffect = when {
        style.endsWith("impact") -> MessageEffect.SLAM
        style.endsWith("loud") -> MessageEffect.LOUD
        style.endsWith("gentle") -> MessageEffect.GENTLE
        style.endsWith("invisibleink") -> MessageEffect.INVISIBLE_INK
        else -> MessageEffect.NONE
    }

    // --- Attachment bytes ---------------------------------------------------

    /**
     * Streams the appended blocks to disk, returning attachment id -> file URI.
     *
     * The blocks are positional: block N belongs to whichever attachment
     * claimed `bytes_id` N. Anything not claimed still has to be read past,
     * or every block after it lands on the wrong file.
     */
    private fun writeAttachments(
        input: InputStream,
        pending: List<PendingAttachment>,
        into: File,
    ): Map<String, String> {
        if (pending.isEmpty()) return emptyMap()
        val byIndex = pending.associateBy { it.bytesId }
        val highest = pending.maxOf { it.bytesId }
        val out = HashMap<String, String>()

        for (index in 0..highest) {
            val length = input.readBigEndianInt() ?: break
            if (length <= 0) continue
            val claim = byIndex[index]
            if (claim == null) {
                input.skipFully(length.toLong())
                continue
            }
            val target = File(into, "${claim.attachmentId.substringAfter(':')}-${claim.fileName}")
            runCatching {
                target.outputStream().use { sink ->
                    input.copyExactly(sink, length.toLong())
                }
                out[claim.attachmentId] = Uri.fromFile(target).toString()
            }.onFailure {
                Log.w(TAG, "couldn't write ${claim.fileName}", it)
            }
        }
        return out
    }

    // --- Merging ------------------------------------------------------------

    private suspend fun merge(
        store: MessageStore,
        parsed: Parsed,
        attachmentUris: Map<String, String>,
    ): Result {
        var duplicates = 0
        var newChats = 0
        var newMessages = 0
        var withBytes = 0

        store.update { existingChats, existingMessages ->
            val haveMessage = existingMessages.mapTo(HashSet()) { it.id }
            val haveChat = existingChats.associateBy { it.id }

            val addedChats = parsed.chats.filterNot { haveChat.containsKey(it.id) }
            newChats = addedChats.size

            val addedMessages = parsed.messages
                .filter { it.id !in haveMessage }
                .map { message ->
                    val resolved = message.attachments.map { att ->
                        val uri = attachmentUris[att.id]
                        if (uri != null) withBytes++
                        att.copy(uri = uri)
                    }
                    message.copy(attachments = resolved)
                }
            duplicates = parsed.messages.size - addedMessages.size
            newMessages = addedMessages.size

            val allMessages = existingMessages + addedMessages
            // Grouped once rather than filtered per chat. A real export runs to
            // tens of thousands of messages, and scanning all of them for every
            // conversation turns the import into a visible freeze.
            val newestPerChat = allMessages
                .groupingBy { it.chatId }
                .reduce { _, best, next -> if (next.timestamp > best.timestamp) next else best }
            val allChats = (existingChats + addedChats).map { chat ->
                chat.copy(lastMessage = newestPerChat[chat.id])
            }
            allChats to allMessages
        }
        store.flush()
        return Result(newChats, newMessages, withBytes, duplicates)
    }

    // --- Stream helpers -----------------------------------------------------

    private fun InputStream.readBigEndianInt(): Int? {
        val buf = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = read(buf, read, 4 - read)
            if (n < 0) return null
            read += n
        }
        return ((buf[0].toInt() and 0xFF) shl 24) or
            ((buf[1].toInt() and 0xFF) shl 16) or
            ((buf[2].toInt() and 0xFF) shl 8) or
            (buf[3].toInt() and 0xFF)
    }

    private fun InputStream.readFully(into: ByteArray) {
        var read = 0
        while (read < into.size) {
            val n = read(into, read, into.size - read)
            if (n < 0) throw IllegalArgumentException("That export is truncated.")
            read += n
        }
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        val scratch = ByteArray(64 * 1024)
        while (left > 0) {
            val n = read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (n < 0) return
            left -= n
        }
    }

    private fun InputStream.copyExactly(sink: java.io.OutputStream, count: Long) {
        var left = count
        val scratch = ByteArray(64 * 1024)
        while (left > 0) {
            val n = read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (n < 0) throw IllegalArgumentException("That export is truncated.")
            sink.write(scratch, 0, n)
            left -= n
        }
    }

    private const val TAG = "OpenBubblesImport"
}

/**
 * Stops a reader at a byte count.
 *
 * The export is a JSON block followed by raw attachment bytes in the same
 * file. A JSON parser handed the underlying stream would read past the end of
 * its document into the attachment data; this ends the stream exactly where
 * the JSON does, so the bytes after it are still there to be read afterwards.
 */
private class BoundedInputStream(
    private val inner: java.io.InputStream,
    private var remaining: Long,
) : java.io.InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = inner.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        val count = inner.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    /** Deliberately does not close the underlying stream - it is read on. */
    override fun close() = Unit
}
