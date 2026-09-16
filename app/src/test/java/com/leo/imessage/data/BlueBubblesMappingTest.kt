package com.leo.imessage.data

import com.leo.imessage.data.BlueBubblesMapping.objects
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The BlueBubbles payload mapping, against payloads shaped like the server's.
 *
 * This is the half of the integration that can be tested without a Mac, and
 * it is also the half where being wrong is quiet: a mis-mapped timestamp
 * gives a conversation in the wrong order, a mis-read receipt gives a
 * permanent "Sending…", and neither throws. So they are pinned here rather
 * than discovered on a phone.
 */
@RunWith(RobolectricTestRunner::class)
class BlueBubblesMappingTest {

    private fun url(guid: String) = "https://example/attachment/$guid"

    @Test
    fun `apple epoch timestamps are shifted into unix time`() {
        // 2001-01-01 in Apple's epoch is 0, and in Unix terms is this.
        assertEquals(978_307_200_000L, BlueBubblesMapping.timestamp(0L + 1L) - 1L)
        // A Core Data timestamp (small) gets shifted.
        val appleSide = 700_000_000_000L
        assertEquals(appleSide + 978_307_200_000L, BlueBubblesMapping.timestamp(appleSide))
        // A value already past 2001 in Unix terms is left alone.
        val unixSide = 1_700_000_000_000L
        assertEquals(unixSide, BlueBubblesMapping.timestamp(unixSide))
    }

    @Test
    fun `a one to one chat takes its name from the participant`() {
        val json = JSONObject(
            """
            {"guid":"iMessage;-;+15551234567","chatIdentifier":"+15551234567","style":45,
             "participants":[{"address":"+15551234567","displayName":"Alex"}]}
            """.trimIndent(),
        )
        val chat = BlueBubblesMapping.chat(json)!!
        assertEquals("iMessage;-;+15551234567", chat.id)
        assertEquals("Alex", chat.displayName)
        assertEquals(1, chat.participants.size)
        assertTrue(!chat.isGroup)
        assertEquals(Service.IMESSAGE, chat.service)
    }

    @Test
    fun `a group chat with no name lists its members`() {
        val json = JSONObject(
            """
            {"guid":"iMessage;+;chat123","style":43,
             "participants":[{"address":"a@b.com","displayName":"Ana"},
                             {"address":"+15550000000","displayName":"Bo"}]}
            """.trimIndent(),
        )
        val chat = BlueBubblesMapping.chat(json)!!
        assertEquals("Ana, Bo", chat.displayName)
        assertTrue(chat.isGroup)
    }

    @Test
    fun `an SMS chat is marked as SMS not iMessage`() {
        val json = JSONObject(
            """{"guid":"SMS;-;+15551112222","style":45,
                "participants":[{"address":"+15551112222"}]}""",
        )
        assertEquals(Service.SMS, BlueBubblesMapping.chat(json)!!.service)
    }

    @Test
    fun `a chat with no guid is not a chat`() {
        assertNull(BlueBubblesMapping.chat(JSONObject("""{"style":45}""")))
    }

    @Test
    fun `an incoming message keeps its sender and text`() {
        val json = JSONObject(
            """
            {"guid":"msg-1","text":"hey","dateCreated":1700000000000,"isFromMe":false,
             "handle":{"address":"+15551234567"}}
            """.trimIndent(),
        )
        val m = BlueBubblesMapping.message(json, "iMessage;-;+15551234567", ::url)!!
        assertEquals("msg-1", m.id)
        assertEquals("hey", m.text)
        assertEquals(false, m.isFromMe)
        assertEquals("+15551234567", m.senderId)
        assertEquals(1700000000000L, m.timestamp)
    }

    @Test
    fun `delivery state prefers read over delivered`() {
        // A read message also carries a delivered date. Checking delivery
        // first would report every read message as merely delivered.
        val json = JSONObject(
            """
            {"guid":"m","text":"x","isFromMe":true,"dateCreated":1700000000000,
             "dateDelivered":1700000001000,"dateRead":1700000002000}
            """.trimIndent(),
        )
        assertEquals(DeliveryState.READ, BlueBubblesMapping.deliveryState(json))
    }

    @Test
    fun `an errored message is failed whatever else it carries`() {
        val json = JSONObject(
            """{"guid":"m","isFromMe":true,"dateCreated":1,"dateDelivered":2,"error":1}""",
        )
        assertEquals(DeliveryState.FAILED, BlueBubblesMapping.deliveryState(json))
    }

    @Test
    fun `a sent message with no receipts is sent, not delivered`() {
        val json = JSONObject("""{"guid":"m","isFromMe":true,"dateCreated":1700000000000}""")
        assertEquals(DeliveryState.SENT, BlueBubblesMapping.deliveryState(json))
    }

    @Test
    fun `a retracted message keeps its text for the reveal`() {
        val json = JSONObject(
            """
            {"guid":"m","text":"the original","isFromMe":true,
             "dateCreated":1700000000000,"dateRetracted":1700000009000}
            """.trimIndent(),
        )
        val m = BlueBubblesMapping.message(json, "c", ::url)!!
        assertEquals("", m.text)
        assertEquals("the original", m.unsentText)
        assertTrue(m.isUnsent)
    }

    @Test
    fun `attachments get a download url and a media kind`() {
        val json = JSONObject(
            """
            {"guid":"m","text":"","isFromMe":false,"dateCreated":1700000000000,
             "attachments":[{"guid":"att-1","transferName":"IMG_0001.HEIC",
                             "mimeType":"image/heic","totalBytes":2048}]}
            """.trimIndent(),
        )
        val m = BlueBubblesMapping.message(json, "c", ::url)!!
        assertEquals(1, m.attachments.size)
        val a = m.attachments.first()
        assertEquals("IMG_0001.HEIC", a.fileName)
        assertEquals(MediaKind.IMAGE, a.kind)
        assertEquals(2048L, a.sizeBytes)
        assertEquals("https://example/attachment/att-1", a.uri)
    }

    @Test
    fun `the attachment placeholder character is stripped from text`() {
        // iMessage puts U+FFFC where an attachment sits; left in, every photo
        // renders a stray box under it.
        val json = JSONObject(
            """{"guid":"m","text":"look ￼","isFromMe":false,"dateCreated":1700000000000}""",
        )
        assertEquals("look", BlueBubblesMapping.message(json, "c", ::url)!!.text.trim())
    }

    @Test
    fun `tapbacks are recognised and not treated as messages`() {
        val json = JSONObject(
            """
            {"guid":"t","text":"Loved “hey”","isFromMe":true,
             "dateCreated":1700000000000,"associatedMessageType":2000,
             "associatedMessageGuid":"p:0/msg-1"}
            """.trimIndent(),
        )
        assertTrue(BlueBubblesMapping.isTapback(json))
        assertEquals("msg-1", BlueBubblesMapping.tapbackTarget(json))
        assertEquals(TapbackKind.HEART, BlueBubblesMapping.tapbackKind(2000))
        assertEquals(false, BlueBubblesMapping.tapbackRemoved(json))
    }

    @Test
    fun `a removed tapback is distinguished from an added one`() {
        val json = JSONObject("""{"guid":"t","associatedMessageType":3000,
                                  "associatedMessageGuid":"p:0/msg-1"}""")
        assertTrue(BlueBubblesMapping.isTapback(json))
        assertTrue(BlueBubblesMapping.tapbackRemoved(json))
    }

    @Test
    fun `an ordinary message is not mistaken for a tapback`() {
        assertTrue(!BlueBubblesMapping.isTapback(JSONObject("""{"guid":"m","text":"hi"}""")))
    }

    @Test
    fun `send effects map to the app's effects`() {
        assertEquals(MessageEffect.SLAM, BlueBubblesMapping.effect("com.apple.MobileSMS.expressivesend.impact"))
        assertEquals(MessageEffect.LOUD, BlueBubblesMapping.effect("com.apple.MobileSMS.expressivesend.loud"))
        assertEquals(
            MessageEffect.INVISIBLE_INK,
            BlueBubblesMapping.effect("com.apple.MobileSMS.expressivesend.invisibleink"),
        )
        assertEquals(MessageEffect.NONE, BlueBubblesMapping.effect(null))
    }

    @Test
    fun `reaction names match what the server expects`() {
        assertEquals("love", BlueBubblesMapping.reactionName(TapbackKind.HEART))
        assertEquals("like", BlueBubblesMapping.reactionName(TapbackKind.THUMBS_UP))
        assertEquals("emphasize", BlueBubblesMapping.reactionName(TapbackKind.EXCLAIM))
    }

    @Test
    fun `objects skips nulls and non-objects in an array`() {
        val arr = JSONArray("""[{"guid":"a"}, null, 5, {"guid":"b"}]""")
        assertEquals(listOf("a", "b"), arr.objects().map { it.optString("guid") })
    }
}
