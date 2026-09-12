package com.leo.imessage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one send failure whose stock wording is misleading enough to be worth
 * a test: it is what the app says when nothing will send, and the version it
 * replaces told people to wait out a rate limit they may not have.
 */
class FailureMessageTest {

    private class Core(message: String) : Exception(message)

    @Test
    fun `an empty lookup is not reported as a rate limit`() {
        val stock = "reason=Could not deliver message. The recipient does not have " +
            "iMessage or you are being rate-limited. Rate limits can start at 0 users " +
            "for brand new accounts."
        val text = humaniseFailure(Core(stock))

        assertTrue("should name the real cause", text.contains("No iMessage account"))
        assertTrue("should say why this isn't a rate limit", text.contains("not the same as a rate"))
        assertTrue("should not tell the user to wait", !text.contains("patience"))
    }

    @Test
    fun `other failures keep their own detail`() {
        assertEquals("Core: file went missing", humaniseFailure(Core("file went missing")))
    }

    @Test
    fun `a failure with no message still names its type`() {
        assertEquals("Core", humaniseFailure(Core("")))
    }
}
