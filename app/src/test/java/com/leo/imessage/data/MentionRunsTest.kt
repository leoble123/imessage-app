package com.leo.imessage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Splitting a composed message into mention runs.
 *
 * Worth testing because the failure is silent and lands on someone else's
 * phone: a run that should have been a mention goes out as text and nobody is
 * notified, or a name in ordinary prose becomes a mention and pings a person
 * who was only being talked about.
 */
class MentionRunsTest {

    private val ava = Contact("1", "Ava", "tel:+15550100001")
    private val marco = Contact("2", "Marco", "tel:+15550100002")
    private val avaChen = Contact("3", "Ava Chen", "tel:+15550100003")

    private fun chat(vararg people: Contact) = Chat(
        id = "c", displayName = "Group", participants = people.toList(), lastMessage = null,
    )

    @Test
    fun `a mention becomes its own run, carrying the handle`() {
        val runs = mentionRuns("@Ava you around", chat(ava, marco), listOf(ava.handle))
        assertEquals(2, runs.size)
        // The name without the sigil: "@" is composer syntax, not message text.
        assertEquals("Ava", runs[0].text)
        assertEquals(ava.handle, runs[0].mentions)
        assertEquals(" you around", runs[1].text)
        assertEquals(null, runs[1].mentions)
    }

    @Test
    fun `everyone expands to a mention each`() {
        val runs = mentionRuns(
            "@Ava @Marco food",
            chat(ava, marco),
            listOf(ava.handle, marco.handle),
        )
        val mentioned = runs.mapNotNull { it.mentions }
        assertEquals(listOf(ava.handle, marco.handle), mentioned)
    }

    @Test
    fun `a name in prose is not a mention`() {
        // Nobody was picked, so nothing pings - even though the text says Ava.
        assertTrue(mentionRuns("ask Ava about it", chat(ava), emptyList()).isEmpty())
    }

    @Test
    fun `the longest name wins`() {
        val runs = mentionRuns(
            "@Ava Chen hi",
            chat(ava, avaChen),
            listOf(ava.handle, avaChen.handle),
        )
        assertEquals("Ava Chen", runs[0].text)
        assertEquals(avaChen.handle, runs[0].mentions)
    }

    @Test
    fun `an edited token falls back to plain text`() {
        // The composer inserted "@Ava" and the writer mangled it since. Sending
        // it as text is right; inventing a mention from the wreckage is not.
        assertTrue(mentionRuns("@Av you around", chat(ava), listOf(ava.handle)).isEmpty())
    }
}
