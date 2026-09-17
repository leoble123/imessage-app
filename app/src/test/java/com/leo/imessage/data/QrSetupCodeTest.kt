package com.leo.imessage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QR setup code parsing, against every shape the scanner can hand it: a
 * legitimate payload of each kind, and the malformed input a bad scan or a
 * QR code from somewhere else would produce.
 */
@RunWith(RobolectricTestRunner::class)
class QrSetupCodeTest {

    @Test
    fun `BlueBubbles array is read as a Mac server`() {
        val payload = QrSetupCode.parse("""["s3cret","https://my-server.example.com"]""")
        assertEquals(QrSetupPayload.MacServer("https://my-server.example.com", "s3cret"), payload)
    }

    @Test
    fun `relay object is read as a relay`() {
        val payload = QrSetupCode.parse("""{"host":"150.136.167.146:5005","code":"abc123"}""")
        assertEquals(QrSetupPayload.Relay("150.136.167.146:5005", "abc123"), payload)
    }

    @Test
    fun `relay object accepts key aliases`() {
        val payload = QrSetupCode.parse("""{"relayHost":"host.example","pairing_code":"xyz"}""")
        assertEquals(QrSetupPayload.Relay("host.example", "xyz"), payload)
    }

    @Test
    fun `mac server object form is read as a Mac server`() {
        val payload = QrSetupCode.parse("""{"server":"https://mac.example","password":"hunter2"}""")
        assertEquals(QrSetupPayload.MacServer("https://mac.example", "hunter2"), payload)
    }

    @Test
    fun `plain host pipe code is read as a relay`() {
        val payload = QrSetupCode.parse("150.136.167.146:5005|abc123")
        assertEquals(QrSetupPayload.Relay("150.136.167.146:5005", "abc123"), payload)
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(QrSetupCode.parse(""))
        assertNull(QrSetupCode.parse("   "))
    }

    @Test
    fun `unrelated text is rejected`() {
        assertNull(QrSetupCode.parse("https://example.com/some/random/link"))
    }

    @Test
    fun `array missing a field is rejected`() {
        assertNull(QrSetupCode.parse("""["onlyOneField"]"""))
    }

    @Test
    fun `array with a blank field is rejected`() {
        assertNull(QrSetupCode.parse("""["","https://host"]"""))
    }

    @Test
    fun `malformed json is rejected rather than thrown`() {
        assertNull(QrSetupCode.parse("{not json"))
        assertNull(QrSetupCode.parse("[not json"))
    }

    @Test
    fun `object with neither shape is rejected`() {
        assertNull(QrSetupCode.parse("""{"unrelated":"data"}"""))
    }

    @Test
    fun `pipe with nothing after it is rejected`() {
        assertNull(QrSetupCode.parse("host|"))
        assertNull(QrSetupCode.parse("|code"))
    }
}
