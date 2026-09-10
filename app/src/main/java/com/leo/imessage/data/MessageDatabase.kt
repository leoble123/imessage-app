package com.leo.imessage.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conversations and messages, on disk, queried rather than held.
 *
 * This replaces keeping every message in one in-memory list. That worked
 * until there was any real history behind it: receiving a single message
 * copied the whole list, republished the whole list, and made every open
 * conversation re-filter the whole list. At a few hundred messages that is
 * invisible; at the fifty thousand a few years of history produces, it is
 * dropped frames and then an out-of-memory kill.
 *
 * Plain SQLite rather than an ORM. The platform ships it, there is no
 * annotation processor in the build, and the schema is small enough that the
 * generated code would be longer than the queries it replaces.
 *
 * Messages are stored half-structured: the fields worth indexing or sorting on
 * get real columns, and everything else rides in a JSON blob. It means one
 * cheap `ORDER BY timestamp LIMIT` for a conversation, without twenty columns
 * that would each need a migration the first time a message gains a field.
 */
class MessageDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Write-ahead logging: a read of the conversation list doesn't block
        // on a message being written, which is exactly the collision that
        // happens when something arrives while you're scrolling.
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chats (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL DEFAULT '',
                participants TEXT NOT NULL DEFAULT '[]',
                unread_count INTEGER NOT NULL DEFAULT 0,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                is_muted INTEGER NOT NULL DEFAULT 0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                service TEXT NOT NULL DEFAULT 'IMESSAGE'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                chat_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_from_me INTEGER NOT NULL DEFAULT 0,
                body TEXT NOT NULL
            )
            """.trimIndent()
        )
        // The one query that has to stay fast no matter how much history there
        // is: the newest messages in a conversation.
        db.execSQL("CREATE INDEX idx_messages_chat_time ON messages(chat_id, timestamp)")
        // Ordering the conversation list by recency.
        db.execSQL("CREATE INDEX idx_messages_time ON messages(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nothing to migrate yet. When there is, it goes here - never a drop,
        // because this is the only copy of the messages that exists.
        Log.i(TAG, "database upgrade $oldVersion -> $newVersion")
    }

    // --- Reads --------------------------------------------------------------

    /**
     * Every conversation, with its newest message attached.
     *
     * One query rather than one per conversation: the list is the first thing
     * drawn on launch, and a query per row is what makes that visibly slow.
     */
    fun chats(): List<Chat> {
        val newest = newestPerChat()
        val out = ArrayList<Chat>()
        readableDatabase.rawQuery("SELECT * FROM chats", null).use { cursor ->
            while (cursor.moveToNext()) {
                val chat = cursor.toChat()
                out += chat.copy(lastMessage = newest[chat.id])
            }
        }
        return out
    }

    fun chat(id: String): Chat? =
        readableDatabase.rawQuery("SELECT * FROM chats WHERE id = ?", arrayOf(id)).use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.toChat().copy(lastMessage = newestIn(id))
        }

    private fun newestPerChat(): Map<String, Message> {
        val out = HashMap<String, Message>()
        readableDatabase.rawQuery(
            """
            SELECT m.* FROM messages m
            JOIN (
                SELECT chat_id, MAX(timestamp) AS newest
                FROM messages GROUP BY chat_id
            ) latest ON m.chat_id = latest.chat_id AND m.timestamp = latest.newest
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursor.toMessage() ?: continue
                // Two messages can share the newest timestamp to the
                // millisecond; either will do, but it must be deterministic.
                val existing = out[message.chatId]
                if (existing == null || message.id > existing.id) out[message.chatId] = message
            }
        }
        return out
    }

    /**
     * The newest message in one conversation.
     *
     * The index on `(chat_id, timestamp)` makes this a seek rather than a
     * scan, which is what lets a message arriving update just its own row in
     * the conversation list instead of recomputing every row.
     */
    fun newestIn(chatId: String): Message? =
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE chat_id = ? ORDER BY timestamp DESC, id DESC LIMIT 1",
            arrayOf(chatId),
        ).use { if (it.moveToFirst()) it.toMessage() else null }

    /**
     * The most recent [limit] messages in a conversation, oldest first.
     *
     * Bounded on purpose. A transcript that loads ten years at once is the
     * same unbounded read this class exists to remove; the screen can only
     * show a page of it either way.
     */
    fun messages(chatId: String, limit: Int = MESSAGE_PAGE): List<Message> {
        val out = ArrayList<Message>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE chat_id = ? ORDER BY timestamp DESC LIMIT ?",
            arrayOf(chatId, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) cursor.toMessage()?.let(out::add)
        }
        return out.asReversed()
    }

    fun message(id: String): Message? =
        readableDatabase.rawQuery("SELECT * FROM messages WHERE id = ?", arrayOf(id)).use {
            if (it.moveToFirst()) it.toMessage() else null
        }

    fun hasMessage(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM messages WHERE id = ? LIMIT 1", arrayOf(id))
            .use { it.moveToFirst() }

    /**
     * Our own recent messages in a conversation, for applying a read receipt.
     *
     * Bounded to the last [RECEIPT_WINDOW]: a receipt only ever moves the tail
     * of a thread, and rewriting ten years of already-read messages every time
     * somebody opens a chat is exactly the unbounded write this class removes.
     */
    fun ownMessages(chatId: String, limit: Int = RECEIPT_WINDOW): List<Message> {
        val out = ArrayList<Message>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE chat_id = ? AND is_from_me = 1 " +
                "ORDER BY timestamp DESC LIMIT ?",
            arrayOf(chatId, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) cursor.toMessage()?.let(out::add)
        }
        return out
    }

    /**
     * Messages still queued to send later.
     *
     * The `LIKE` is a pre-filter, not the test: `scheduledFor` is only written
     * when it is set, so the substring narrows the rows that have to be parsed
     * and the parsed value decides.
     */
    fun scheduled(): List<Message> {
        val out = ArrayList<Message>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE is_from_me = 1 AND body LIKE '%\"scheduledFor\"%'",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.toMessage()?.takeIf { it.scheduledFor != null }?.let(out::add)
            }
        }
        return out
    }

    fun messageCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun chatCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM chats", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    /** Every attachment URI still referenced, for pruning orphaned files. */
    fun referencedAttachmentUris(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery(
            // Cheap pre-filter in SQL so the JSON parse only runs on rows that
            // could possibly contain one.
            "SELECT body FROM messages WHERE body LIKE '%\"uri\"%'", null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    JSONObject(cursor.getString(0)).optJSONArray("attachments")
                        ?.let { array ->
                            for (i in 0 until array.length()) {
                                array.optJSONObject(i)?.optString("uri")
                                    ?.takeIf { it.isNotEmpty() }?.let(out::add)
                            }
                        }
                }
            }
        }
        return out
    }

    /** Ids of conversations with nobody in them - they can't send or receive. */
    fun participantlessChatIds(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT id FROM chats WHERE participants = '[]' OR participants IS NULL", null,
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.getString(0)
        }
        return out
    }

    // --- Writes -------------------------------------------------------------

    fun upsertChat(chat: Chat) {
        writableDatabase.insertWithOnConflict(
            "chats", null, chat.toRow(), SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertMessage(message: Message) {
        writableDatabase.insertWithOnConflict(
            "messages", null, message.toRow(), SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertMessages(messages: List<Message>) = transaction { db ->
        // One transaction, not one per row. An import of fifty thousand
        // messages is otherwise fifty thousand disk syncs.
        messages.forEach {
            db.insertWithOnConflict("messages", null, it.toRow(), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun upsertChats(chats: List<Chat>) = transaction { db ->
        chats.forEach {
            db.insertWithOnConflict("chats", null, it.toRow(), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteMessage(id: String) {
        writableDatabase.delete("messages", "id = ?", arrayOf(id))
    }

    fun deleteChat(id: String) = transaction { db ->
        db.delete("messages", "chat_id = ?", arrayOf(id))
        db.delete("chats", "id = ?", arrayOf(id))
    }

    fun deleteChats(ids: Collection<String>) = transaction { db ->
        ids.forEach {
            db.delete("messages", "chat_id = ?", arrayOf(it))
            db.delete("chats", "id = ?", arrayOf(it))
        }
    }

    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val TAG = "MessageDatabase"
        const val NAME = "relay.db"
        const val VERSION = 1

        /**
         * How much of a conversation is held at once.
         *
         * Generous enough that scrolling rarely reaches the end, small enough
         * that a single thread can't reintroduce the unbounded read.
         */
        const val MESSAGE_PAGE = 1000

        /** How far back a delivery receipt is allowed to reach. */
        const val RECEIPT_WINDOW = 200
    }
}

// --- Row mapping -------------------------------------------------------------

private fun Chat.toRow() = ContentValues().apply {
    put("id", id)
    put("display_name", displayName)
    put(
        "participants",
        JSONArray().apply { participants.forEach { put(it.toContactJson()) } }.toString(),
    )
    put("unread_count", unreadCount)
    put("is_pinned", if (isPinned) 1 else 0)
    put("is_muted", if (isMuted) 1 else 0)
    put("is_archived", if (isArchived) 1 else 0)
    put("service", service.name)
}

private fun Message.toRow() = ContentValues().apply {
    put("id", id)
    put("chat_id", chatId)
    put("timestamp", timestamp)
    put("is_from_me", if (isFromMe) 1 else 0)
    // Everything not worth a column of its own. Reuses the same serializer the
    // file format used, so the two can't drift apart.
    put("body", toJson().toString())
}

private fun Cursor.toMessage(): Message? = runCatching {
    JSONObject(getString(getColumnIndexOrThrow("body"))).toMessage()
}.getOrNull()

/** `lastMessage` is derived, never stored - the caller attaches it. */
private fun Cursor.toChat() = Chat(
    id = getString(getColumnIndexOrThrow("id")),
    displayName = getString(getColumnIndexOrThrow("display_name")),
    participants = runCatching {
        JSONArray(getString(getColumnIndexOrThrow("participants"))).map { it.toContact() }
    }.getOrDefault(emptyList()),
    lastMessage = null,
    unreadCount = getInt(getColumnIndexOrThrow("unread_count")),
    isPinned = getInt(getColumnIndexOrThrow("is_pinned")) != 0,
    isMuted = getInt(getColumnIndexOrThrow("is_muted")) != 0,
    isArchived = getInt(getColumnIndexOrThrow("is_archived")) != 0,
    service = runCatching {
        Service.valueOf(getString(getColumnIndexOrThrow("service")))
    }.getOrDefault(Service.IMESSAGE),
)

private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i ->
        runCatching { transform(getJSONObject(i)) }.getOrNull()
    }
