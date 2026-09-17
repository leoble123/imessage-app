package com.leo.imessage.data

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- relay tokens ---------------------------------------------------

    @Test
    fun `relay object carries a token when there is one`() {
        val payload = QrSetupCode.parse("""{"host":"h.example","code":"abc","token":"tok"}""")
        assertEquals(QrSetupPayload.Relay("h.example", "abc", "tok"), payload)
    }

    @Test
    fun `host pipe code pipe token is read as a relay with a token`() {
        val payload = QrSetupCode.parse("h.example|abc|tok")
        assertEquals(QrSetupPayload.Relay("h.example", "abc", "tok"), payload)
    }

    @Test
    fun `a relay without a token has none`() {
        assertEquals(QrSetupPayload.Relay("h.example", "abc", null), QrSetupCode.parse("h.example|abc"))
    }

    // --- OpenAbsinthe hardware exports ----------------------------------

    /** The header a real export carries: the tag, then the shared flag. */
    private fun oabsBlob(): ByteArray =
        "OABS".toByteArray() + byteArrayOf(0) + ByteArray(40) { it.toByte() }

    @Test
    fun `a pasted base64 hardware export is recognised`() {
        val base64 = Base64.encodeToString(oabsBlob(), Base64.NO_WRAP)
        assertEquals(QrSetupPayload.MacHardware(base64), QrSetupCode.parse(base64))
    }

    @Test
    fun `a pasted export survives the line breaks a copy leaves in`() {
        val base64 = Base64.encodeToString(oabsBlob(), Base64.NO_WRAP)
        val wrapped = base64.chunked(20).joinToString("\n  ")
        assertEquals(QrSetupPayload.MacHardware(base64), QrSetupCode.parse(wrapped))
    }

    /**
     * The case that made the scanner look broken: an export's QR carries raw
     * bytes, so ML Kit hands back a null text value and only the bytes.
     */
    @Test
    fun `a scanned hardware export is read from the bytes alone`() {
        val payload = QrSetupCode.parseScan(text = null, bytes = oabsBlob())
        assertTrue(payload is QrSetupPayload.MacHardware)
        val decoded = Base64.decode((payload as QrSetupPayload.MacHardware).base64, Base64.DEFAULT)
        assertTrue(decoded.contentEquals(oabsBlob()))
    }

    @Test
    fun `a scanned text code still parses as text`() {
        val payload = QrSetupCode.parseScan(text = "h.example|abc", bytes = "h.example|abc".toByteArray())
        assertEquals(QrSetupPayload.Relay("h.example", "abc", null), payload)
    }

    @Test
    fun `a scan with nothing in it is rejected`() {
        assertNull(QrSetupCode.parseScan(text = null, bytes = null))
        assertNull(QrSetupCode.parseScan(text = "", bytes = null))
    }

    @Test
    fun `base64 that isn't an export is rejected`() {
        val notOabs = Base64.encodeToString("hello there, world".toByteArray(), Base64.NO_WRAP)
        assertNull(QrSetupCode.parse(notOabs))
    }
}
