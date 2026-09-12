package com.leo.imessage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample conversations are written by hand into the same database the
 * real ones live in, which makes two mistakes possible and both of them bad:
 * an id that isn't prefixed can never be removed, and a message pointing at a
 * conversation that isn't there is a row nothing will ever show.
 */
class SampleDataTest {

    private val built = SampleData.build()
    private val chats = built.first
    private val messages = built.second

    @Test
    fun `everything is removable`() {
        chats.forEach { assertTrue("${it.id} is not prefixed", SampleData.isSample(it.id)) }
        messages.forEach {
            assertTrue("${it.id} is not prefixed", it.id.startsWith(SampleData.PREFIX))
            assertTrue("${it.chatId} is not prefixed", SampleData.isSample(it.chatId))
        }
    }

    @Test
    fun `no message is orphaned`() {
        val known = chats.map { it.id }.toSet()
        messages.forEach {
            assertTrue("${it.id} points at ${it.chatId}, which does not exist", it.chatId in known)
        }
    }

    @Test
    fun `no id is used twice`() {
        assertEquals(messages.size, messages.map { it.id }.toSet().size)
        assertEquals(chats.size, chats.map { it.id }.toSet().size)
    }

    @Test
    fun `every conversation has somebody in it`() {
        // A conversation with no participants is swept away on the next
        // launch, so one seeded that way would silently not be there.
        chats.forEach { assertTrue("${it.id} has nobody in it", it.participants.isNotEmpty()) }
    }

    @Test
    fun `a reply points at a message that exists`() {
        val known = messages.map { it.id }.toSet()
        messages.mapNotNull { it.replyToId }.forEach {
            assertTrue("reply points at $it, which does not exist", it in known)
        }
    }

    @Test
    fun `senders are participants of their own conversation`() {
        val people = chats.associate { chat -> chat.id to chat.participants.map { it.id }.toSet() }
        messages.filterNot { it.isFromMe }.forEach { message ->
            assertTrue(
                "${message.senderId} does not belong to ${message.chatId}",
                message.senderId in people.getValue(message.chatId),
            )
        }
    }

    @Test
    fun `every conversation has something in it`() {
        val counts = messages.groupingBy { it.chatId }.eachCount()
        chats.forEach { assertTrue("${it.id} is empty", counts.getOrDefault(it.id, 0) > 0) }
    }

    @Test
    fun `the unread counts are things the list can actually draw`() {
        chats.forEach {
            val waiting = messages.count { m -> m.chatId == it.id && !m.isFromMe }
            assertTrue(
                "${it.id} claims ${it.unreadCount} unread out of $waiting from them",
                it.unreadCount <= waiting,
            )
        }
    }
}
