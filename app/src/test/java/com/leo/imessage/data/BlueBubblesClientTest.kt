package com.leo.imessage.data

import com.leo.imessage.data.BlueBubblesMapping.objects
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * The BlueBubbles client against a real socket.
 *
 * A stand-in for the Mac, speaking the same envelope and the same routes. It
 * is the only way to exercise this code without a Mac that is awake, and the
 * bugs it catches - a wrong route, a body the server would reject, an error
 * envelope treated as success - are exactly the ones that otherwise surface
 * as "nothing happens" on a phone with no way to see why.
 *
 * Written on ServerSocket rather than com.sun.net.httpserver, which Android's
 * stubbed JDK does not provide to unit tests.
 */
@RunWith(RobolectricTestRunner::class)
class BlueBubblesClientTest {

    /** One request as the fake server saw it. */
    data class Seen(val method: String, val path: String, val query: String, val body: String)

    private lateinit var socket: ServerSocket
    private lateinit var client: BlueBubblesClient
    private val seen = mutableListOf<Seen>()

    /** Route (path without /api/v1) -> the `data` field to answer with. */
    private val routes = mutableMapOf<String, String>()

    /** Set to answer every request this way instead, for failure cases. */
    private var override: Pair<Int, String>? = null

    @Before
    fun setUp() {
        socket = ServerSocket(0)
        thread(isDaemon = true) { serve() }
        client = BlueBubblesClient("http://127.0.0.1:${socket.localPort}", "hunter2")
    }

    @After
    fun tearDown() {
        runCatching { socket.close() }
    }

    private fun serve() {
        while (!socket.isClosed) {
            val conn = try {
                socket.accept()
            } catch (e: SocketException) {
                return
            }
            conn.use {
                val input = it.getInputStream()
                val head = StringBuilder()
                // Read the request line and headers byte by byte; the body is
                // read separately by Content-Length so we never over-read.
                while (!head.endsWith("\r\n\r\n")) {
                    val b = input.read()
                    if (b == -1) return@use
                    head.append(b.toChar())
                }
                val lines = head.toString().split("\r\n")
                val (method, target) = lines[0].split(" ").let { p -> p[0] to p[1] }
                val chunked = lines.any { l ->
                    l.startsWith("Transfer-Encoding:", true) && l.contains("chunked", true)
                }
                val length = lines.firstOrNull { l -> l.startsWith("Content-Length:", true) }
                    ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                val body = if (chunked) {
                    // Multipart uploads are streamed, so they arrive as chunks
                    // with no declared length - read until the terminating
                    // zero-length chunk, as a real server does.
                    val sb = StringBuilder()
                    while (true) {
                        val sizeLine = StringBuilder()
                        while (!sizeLine.endsWith("\r\n")) {
                            val b = input.read()
                            if (b == -1) break
                            sizeLine.append(b.toChar())
                        }
                        val size = sizeLine.toString().trim().substringBefore(';')
                            .toIntOrNull(16) ?: 0
                        if (size == 0) break
                        val buf = ByteArray(size)
                        var read = 0
                        while (read < size) {
                            val n = input.read(buf, read, size - read)
                            if (n <= 0) break
                            read += n
                        }
                        sb.append(String(buf, 0, read))
                        input.read(); input.read() // trailing CRLF
                    }
                    sb.toString()
                } else if (length > 0) {
                    val buf = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(buf, read, length - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""

                // Decoded, the way a real server hands the path to its
                // router. The client percent-encodes chat guids because they
                // contain ';' and '+', and matching the raw encoded form here
                // would be testing the encoder rather than the route.
                val path = java.net.URLDecoder.decode(
                    target.substringBefore("?").removePrefix("/api/v1"), "UTF-8",
                )
                val query = target.substringAfter("?", "")
                synchronized(seen) { seen += Seen(method, path, query, body) }

                val (code, payload) = override ?: run {
                    val canned = routes[path]
                    if (canned == null) 404 to """{"status":404,"message":"Not found","data":null}"""
                    else 200 to """{"status":200,"message":"Success","data":$canned}"""
                }
                val bytes = payload.toByteArray()
                val response = "HTTP/1.1 $code OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                it.getOutputStream().apply {
                    write(response.toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }
    }

    private fun only(): Seen = synchronized(seen) { seen.single() }

    @Test
    fun `ping succeeds when the server answers`() {
        routes["/ping"] = "\"pong\""
        assertTrue(client.ping())
        assertEquals("GET", only().method)
    }

    @Test
    fun `ping fails rather than throwing when the server is absent`() {
        socket.close()
        assertTrue(!client.ping())
    }

    @Test
    fun `the password is sent on every request`() {
        routes["/ping"] = "\"pong\""
        client.ping()
        assertTrue(only().query.contains("password=hunter2"))
    }

    @Test
    fun `chats are requested with participants and last message inlined`() {
        routes["/chat/query"] = """[{"guid":"iMessage;-;+1555","style":45,
            "participants":[{"address":"+1555","displayName":"Alex"}]}]"""
        assertEquals(1, client.chats().objects().size)

        val req = only()
        assertEquals("POST", req.method)
        assertEquals("/chat/query", req.path)
        assertTrue(req.body.contains("participants"))
        assertTrue(req.body.contains("lastmessage"))
    }

    @Test
    fun `messages for a chat go to the chat's own route`() {
        routes["/chat/iMessage;-;+1555/message"] =
            """[{"guid":"m1","text":"hi","dateCreated":1700000000000,"isFromMe":false}]"""
        assertEquals(1, client.messages("iMessage;-;+1555").objects().size)
        assertEquals("GET", only().method)
    }

    @Test
    fun `polling asks only for messages after the watermark`() {
        routes["/message/query"] = "[]"
        client.messagesSince(1700000000000L)
        assertTrue(only().body.contains("\"after\":1700000000000"))
    }

    @Test
    fun `sending posts the fields the server requires`() {
        routes["/message/text"] = """{"guid":"server-guid-1"}"""
        val result = client.sendText("iMessage;-;+1555", "relay-abc", "hello")
        assertNotNull(result)
        assertEquals("server-guid-1", result!!.optString("guid"))

        val req = only()
        assertEquals("POST", req.method)
        assertEquals("/message/text", req.path)
        assertTrue(req.body.contains("\"chatGuid\":\"iMessage;-;+1555\""))
        assertTrue(req.body.contains("\"tempGuid\":\"relay-abc\""))
        assertTrue(req.body.contains("\"message\":\"hello\""))
    }

    @Test
    fun `a reply carries the message it is replying to`() {
        routes["/message/text"] = """{"guid":"g"}"""
        client.sendText("c", "t", "sure", replyToGuid = "earlier-guid")
        assertTrue(only().body.contains("\"selectedMessageGuid\":\"earlier-guid\""))
    }

    @Test
    fun `a reaction names the message and the reaction`() {
        routes["/message/react"] = """{"guid":"g"}"""
        client.react("chat-1", "msg-1", "hey", "love")
        val req = only()
        assertEquals("/message/react", req.path)
        assertTrue(req.body.contains("\"reaction\":\"love\""))
        assertTrue(req.body.contains("\"selectedMessageGuid\":\"msg-1\""))
    }

    @Test
    fun `a failure envelope is raised rather than returned as success`() {
        // 200 OK carrying a failing status is the shape that quietly breaks
        // clients which only check the HTTP code.
        override = 200 to """{"status":500,"message":"Failed to send","data":null}"""
        try {
            client.sendText("c", "t", "x")
            fail("expected an exception")
        } catch (e: BlueBubblesClient.ApiException) {
            assertTrue(e.message!!.contains("Failed to send"))
        }
    }

    @Test
    fun `an expired tunnel is reported in words rather than as a parse error`() {
        override = 502 to "<html>Bad gateway</html>"
        try {
            client.chats()
            fail("expected an exception")
        } catch (e: BlueBubblesClient.ApiException) {
            assertTrue(e.message!!.contains("trycloudflare"))
        }
    }

    @Test
    fun `an attachment is uploaded as multipart with the file's bytes`() {
        routes["/message/attachment"] = """{"guid":"g"}"""
        val file = java.io.File.createTempFile("relay", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            deleteOnExit()
        }

        client.sendAttachment("chat-1", "relay-1", file, "photo.png", "image/png")

        val req = only()
        assertEquals("POST", req.method)
        assertEquals("/message/attachment", req.path)
        // The fields the server needs, and the file part itself.
        assertTrue(req.body.contains("name=\"chatGuid\""))
        assertTrue(req.body.contains("chat-1"))
        assertTrue(req.body.contains("filename=\"photo.png\""))
        assertTrue(req.body.contains("Content-Type: image/png"))
        // PNG's magic number, proving the bytes went with it rather than
        // just the metadata.
        assertTrue(req.body.contains("PNG"))
    }

    @Test
    fun `the origin is normalised and a bare host gets https`() {
        assertEquals("https://example.com", BlueBubblesClient("example.com/", "p").origin)
        assertEquals("http://1.2.3.4:1234", BlueBubblesClient("http://1.2.3.4:1234", "p").origin)
    }

    @Test
    fun `an attachment url carries the password so the image loads`() {
        val url = client.attachmentUrl("att-1")
        assertTrue(url.contains("/attachment/att-1/download"))
        assertTrue(url.contains("password=hunter2"))
    }
}
