package com.leo.imessage.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The storage layer, against real SQLite.
 *
 * These are the tests worth having in this app before any others. Everything
 * else it does can be retried - a send that fails can be sent again, a call
 * that drops can be redialled - but iMessage never resends history, so a bug
 * in here destroys the only copy of a conversation that exists anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageStoreTest {

    private lateinit var context: android.content.Context
    private lateinit var legacy: File
    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("relay.db")
        legacy = File(context.filesDir, "history.json")
        legacy.delete()
        File(context.filesDir, "history.json.migrated").delete()
        store = MessageStore(context, legacy)
    }

    @After
    fun tearDown() {
        context.deleteDatabase("relay.db")
        legacy.delete()
        File(context.filesDir, "history.json.migrated").delete()
    }

    // --- Basics ---------------------------------------------------------------

    @Test
    fun `a message survives a round trip`() = runTest {
        store.addChat(chat("chat:a"))
        assertTrue(store.insert(message("m1", "chat:a", text = "hello", at = 100)))

        val read = store.messages("chat:a")
        assertEquals(1, read.size)
        assertEquals("hello", read[0].text)
        assertEquals(100L, read[0].timestamp)
    }

    @Test
    fun `every field of a message survives a round trip`() = runTest {
        store.addChat(chat("chat:a"))
        val original = message("m1", "chat:a").copy(
            text = "the original",
            effect = MessageEffect.SLAM,
            service = Service.SMS,
            deliveryState = DeliveryState.READ,
            replyToId = "m0",
            editHistory = listOf("a draft"),
            editedAt = 12345L,
            isBookmarked = true,
            isPinned = true,
            note = "a private note",
            remindAt = 999L,
            failureReason = "no route",
            textPart = 3,
            tapbacks = listOf(Tapback(TapbackKind.ANY_EMOJI, fromMe = true, senderId = "me", emoji = "🎉")),
            attachments = listOf(
                Attachment("a1", "photo.heic", "image/heic", uri = "file:///tmp/photo", sizeBytes = 42)
            ),
            poll = Poll("lunch?", listOf(PollOption("o1", "tacos", listOf("me")))),
        )
        store.insert(original)

        assertEquals(original, store.message("m1"))
    }

    @Test
    fun `the same message arriving twice is stored once`() = runTest {
        store.addChat(chat("chat:a"))
        assertTrue(store.insert(message("m1", "chat:a")))
        assertFalse(store.insert(message("m1", "chat:a", text = "a redelivery")))

        assertEquals(1, store.messages("chat:a").size)
        assertEquals("hi", store.message("m1")!!.text)
    }

    @Test
    fun `a redelivery does not raise the unread badge twice`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("m1", "chat:a"), incrementUnread = true)
        store.insert(message("m1", "chat:a"), incrementUnread = true)

        assertEquals(1, store.chat("chat:a")!!.unreadCount)
    }

    @Test
    fun `a conversation carries its newest message`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("old", "chat:a", text = "first", at = 100))
        store.insert(message("new", "chat:a", text = "second", at = 200))

        assertEquals("second", store.chat("chat:a")!!.lastMessage?.text)
    }

    @Test
    fun `deleting the newest message moves the conversation back to the one before`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("old", "chat:a", text = "first", at = 100))
        store.insert(message("new", "chat:a", text = "second", at = 200))

        store.deleteMessage("new")

        assertEquals("first", store.chat("chat:a")!!.lastMessage?.text)
    }

    @Test
    fun `deleting a conversation takes its messages with it`() = runTest {
        store.addChat(chat("chat:a"))
        store.addChat(chat("chat:b"))
        store.insert(message("m1", "chat:a"))
        store.insert(message("m2", "chat:b"))

        store.deleteChat("chat:a")

        assertNull(store.chat("chat:a"))
        assertNull(store.message("m1"))
        // The other conversation is untouched.
        assertEquals("m2", store.message("m2")?.id)
    }

    @Test
    fun `a transcript comes back oldest first`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("c", "chat:a", at = 300))
        store.insert(message("a", "chat:a", at = 100))
        store.insert(message("b", "chat:a", at = 200))

        assertEquals(listOf("a", "b", "c"), store.messages("chat:a").map { it.id })
    }

    @Test
    fun `a transcript holds only its own conversation`() = runTest {
        store.addChat(chat("chat:a"))
        store.addChat(chat("chat:b"))
        store.insert(message("m1", "chat:a"))
        store.insert(message("m2", "chat:b"))

        assertEquals(listOf("m1"), store.messages("chat:a").map { it.id })
    }

    // --- Adopting Apple's GUID ------------------------------------------------

    @Test
    fun `a sent message takes on the GUID Apple gave it`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("local-1", "chat:a", text = "hi there", fromMe = true))

        store.adoptGuid("local-1", "GUID-1", DeliveryState.SENT)

        assertNull(store.message("local-1"))
        val adopted = store.message("GUID-1")!!
        assertEquals("hi there", adopted.text)
        assertEquals(DeliveryState.SENT, adopted.deliveryState)
        assertEquals(1, store.messages("chat:a").size)
    }

    /**
     * The race this exists for: Apple can fan our own message back to us
     * before the send call returns. Renaming the placeholder onto the echo's
     * id would leave two messages sharing one id, which the transcript keys
     * by - so it takes the whole screen down rather than showing a duplicate.
     */
    @Test
    fun `an echo that arrived first is not duplicated by the send returning`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(
            message("local-1", "chat:a", fromMe = true).copy(
                isBookmarked = true,
                note = "remember this",
                attachments = listOf(Attachment("a1", "photo.jpg", "image/jpeg", uri = "file:///photo")),
            )
        )
        // Apple's own copy, back down the push connection first.
        store.insert(message("GUID-1", "chat:a", fromMe = true).copy(deliveryState = DeliveryState.DELIVERED))

        store.adoptGuid("local-1", "GUID-1", DeliveryState.SENT)

        assertEquals(1, store.messages("chat:a").size)
        val kept = store.message("GUID-1")!!
        // Delivery only moves forward.
        assertEquals(DeliveryState.DELIVERED, kept.deliveryState)
        // The local-only fields Apple's copy has never heard of.
        assertTrue(kept.isBookmarked)
        assertEquals("remember this", kept.note)
        // And the local file, so a sent photo keeps its preview.
        assertEquals("file:///photo", kept.attachments.single().uri)
    }

    @Test
    fun `adopting a GUID for a message that is gone does nothing`() = runTest {
        store.addChat(chat("chat:a"))
        store.adoptGuid("local-1", "GUID-1", DeliveryState.SENT)
        assertNull(store.message("GUID-1"))
    }

    // --- Receipts -------------------------------------------------------------

    @Test
    fun `a read receipt marks our own messages and leaves theirs alone`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("mine", "chat:a", fromMe = true).copy(deliveryState = DeliveryState.SENT))
        store.insert(message("theirs", "chat:a", fromMe = false).copy(deliveryState = DeliveryState.DELIVERED))

        store.updateOwnMessages("chat:a") { it.copy(deliveryState = DeliveryState.READ) }

        assertEquals(DeliveryState.READ, store.message("mine")!!.deliveryState)
        assertEquals(DeliveryState.DELIVERED, store.message("theirs")!!.deliveryState)
    }

    @Test
    fun `returning null from a bulk update leaves the message untouched`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("mine", "chat:a", fromMe = true).copy(deliveryState = DeliveryState.READ))

        // What a late "delivered" arriving after a "read" does: nothing.
        store.updateOwnMessages("chat:a") { msg ->
            if (msg.deliveryState < DeliveryState.DELIVERED) {
                msg.copy(deliveryState = DeliveryState.DELIVERED)
            } else {
                null
            }
        }

        assertEquals(DeliveryState.READ, store.message("mine")!!.deliveryState)
    }

    // --- Conversations --------------------------------------------------------

    @Test
    fun `adding a conversation that is already here keeps the one that is here`() = runTest {
        store.addChat(chat("chat:a").copy(displayName = "Priya"))
        store.addChat(chat("chat:a").copy(displayName = "not this"))

        assertEquals(1, store.chatsNow().size)
        assertEquals("Priya", store.chat("chat:a")!!.displayName)
    }

    @Test
    fun `a typing indicator is never written to disk`() = runTest {
        store.addChat(chat("chat:a"))
        store.updateChat("chat:a") { it.copy(isTyping = true) }
        assertTrue(store.chat("chat:a")!!.isTyping)

        // A message landing re-reads the row from the database, which is where
        // this used to be dropped - the bubble would vanish mid-typing.
        store.insert(message("m1", "chat:a"))
        assertTrue(store.chat("chat:a")!!.isTyping)

        // But it is not history, so it does not come back on the next launch.
        assertFalse(reopen().chat("chat:a")!!.isTyping)
    }

    @Test
    fun `conversations with nobody in them are cleared out`() = runTest {
        store.addChat(chat("chat:a"))
        store.addChat(chat("chat:broken").copy(participants = emptyList()))

        assertEquals(1, store.removeParticipantlessChats())
        assertEquals(listOf("chat:a"), store.chatsNow().map { it.id })
    }

    // --- Change notifications -------------------------------------------------

    @Test
    fun `a change names the conversation it touched`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("m1", "chat:a"))

        assertEquals("chat:a", store.changes.value.chatId)
    }

    @Test
    fun `every write advances the serial`() = runTest {
        store.addChat(chat("chat:a"))
        val before = store.changes.value.serial
        store.insert(message("m1", "chat:a"))
        val after = store.changes.value.serial
        assertTrue("serial went $before -> $after", after > before)
    }

    // --- Queries the app depends on -------------------------------------------

    @Test
    fun `scheduled messages are found again after a restart`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("later", "chat:a", fromMe = true).copy(scheduledFor = 5_000L))
        store.insert(message("now", "chat:a", fromMe = true))

        val reopened = reopen()
        assertEquals(listOf("later"), reopened.scheduledMessages().map { it.id })
    }

    @Test
    fun `attachment files still pointed at are not pruned`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(
            message("m1", "chat:a").copy(
                attachments = listOf(
                    Attachment("a1", "kept.jpg", "image/jpeg", uri = "file:///kept.jpg"),
                    // Not downloaded yet, so there is no file to keep.
                    Attachment("a2", "pending.jpg", "image/jpeg", uri = null),
                )
            )
        )
        store.insert(message("m2", "chat:a").copy(attachments = emptyList()))

        assertEquals(setOf("file:///kept.jpg"), store.referencedAttachmentUris())
    }

    @Test
    fun `a transcript is capped rather than read whole`() = runTest {
        store.addChat(chat("chat:a"))
        repeat(20) { i -> store.insert(message("m$i", "chat:a", at = i.toLong())) }

        val page = store.messages("chat:a", limit = 5)
        assertEquals(5, page.size)
        // The newest five, still oldest-first.
        assertEquals(listOf("m15", "m16", "m17", "m18", "m19"), page.map { it.id })
    }

    // --- Migrating off the old file -------------------------------------------

    @Test
    fun `the old history file is read into the database and kept aside`() = runTest {
        legacy.writeText(
            buildString {
                appendLine(
                    """{"version":2,"chats":[{"id":"chat:a","displayName":"Priya",""" +
                        """"participants":[{"id":"c1","displayName":"Priya","handle":"tel:+15555550142"}]}]}"""
                )
                appendLine("""{"id":"m1","chatId":"chat:a","text":"from the old file","timestamp":100}""")
                appendLine("""{"id":"m2","chatId":"chat:a","text":"and another","timestamp":200}""")
            }
        )

        val migrated = MessageStore(context, legacy)

        assertEquals(listOf("m1", "m2"), migrated.messages("chat:a").map { it.id })
        assertEquals("Priya", migrated.chat("chat:a")!!.displayName)
        assertEquals("and another", migrated.chat("chat:a")!!.lastMessage?.text)
        assertNull(migrated.loadFailure)

        // Kept, not deleted: this is the only copy the conversation ever had.
        assertFalse(legacy.exists())
        assertTrue(File(context.filesDir, "history.json.migrated").exists())
    }

    @Test
    fun `one unreadable line loses one message and not the history`() = runTest {
        legacy.writeText(
            buildString {
                appendLine("""{"version":2,"chats":[{"id":"chat:a","displayName":"Priya"}]}""")
                appendLine("""{"id":"m1","chatId":"chat:a","text":"fine","timestamp":100}""")
                appendLine("""{"id":"m2","chatId":"chat:a","text":"trunca""")
                appendLine("""{"id":"m3","chatId":"chat:a","text":"also fine","timestamp":300}""")
            }
        )

        val migrated = MessageStore(context, legacy)

        assertEquals(listOf("m1", "m3"), migrated.messages("chat:a").map { it.id })
    }

    @Test
    fun `migrating twice does not happen`() = runTest {
        legacy.writeText(
            buildString {
                appendLine("""{"version":2,"chats":[{"id":"chat:a","displayName":"Priya"}]}""")
                appendLine("""{"id":"m1","chatId":"chat:a","text":"first","timestamp":100}""")
            }
        )
        MessageStore(context, legacy)

        // Something restored the old file - a backup, a file manager. It must
        // not overwrite what has happened since.
        legacy.writeText(
            buildString {
                appendLine("""{"version":2,"chats":[{"id":"chat:a","displayName":"Priya"}]}""")
                appendLine("""{"id":"m1","chatId":"chat:a","text":"a stale copy","timestamp":100}""")
            }
        )
        val second = MessageStore(context, legacy)

        assertEquals("first", second.message("m1")!!.text)
        assertFalse(legacy.exists())
    }

    @Test
    fun `an import merges into what is already here`() = runTest {
        store.addChat(chat("chat:a"))
        store.insert(message("live", "chat:a", text = "sent today", at = 500))

        store.importInto(
            chats = listOf(chat("chat:b").copy(displayName = "Imported")),
            messages = listOf(
                message("old-1", "chat:b", text = "years ago", at = 100),
                message("old-2", "chat:b", text = "also years ago", at = 200),
            ),
        )

        assertEquals(setOf("chat:a", "chat:b"), store.chatsNow().map { it.id }.toSet())
        assertEquals("sent today", store.chat("chat:a")!!.lastMessage?.text)
        assertEquals("also years ago", store.chat("chat:b")!!.lastMessage?.text)
    }

    // --- Helpers ----------------------------------------------------------------

    /** A second store over the same database, i.e. the next launch. */
    private fun reopen() = MessageStore(context, legacy)

    private fun chat(id: String) = Chat(
        id = id,
        displayName = "Priya",
        participants = listOf(Contact("c1", "Priya", "tel:+15555550142")),
        lastMessage = null,
    )

    private fun message(
        id: String,
        chatId: String,
        text: String = "hi",
        at: Long = 1_000L,
        fromMe: Boolean = false,
    ) = Message(
        id = id,
        chatId = chatId,
        text = text,
        timestamp = at,
        isFromMe = fromMe,
        senderId = if (fromMe) "me" else "c1",
    )
}
