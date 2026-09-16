package com.leo.imessage.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The BlueBubbles server's REST API.
 *
 * A completely different shape of backend from the rustpush one. There, this
 * phone *is* an iMessage device: it registers with Apple, holds a
 * cryptographic identity, and needs validation data proving it is real Apple
 * hardware - which is the wall that stops anyone without a Mac. Here, a Mac
 * that is already a legitimate iMessage client does all of that, and this is
 * a remote control for it. No registration, no validation data, no identity,
 * nothing for Apple to accept or refuse.
 *
 * Written against HttpURLConnection and org.json rather than a client
 * library: both ship with Android, and the build runs offline, so a new
 * dependency is a new way for the build to fail for no benefit at this size.
 */
class BlueBubblesClient(
    baseUrl: String,
    private val password: String,
) {
    /** Normalised once: no trailing slash, scheme always present. */
    val origin: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }

    private val apiRoot = "$origin/api/v1"

    class ApiException(message: String, val status: Int? = null) : Exception(message)

    // --- transport ------------------------------------------------------

    /**
     * Every response is `{status, message, data}`, and `status` is the
     * server's own opinion rather than the HTTP code - a 200 can still carry
     * a failure. Both are checked.
     */
    private fun request(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: JSONObject? = null,
        timeoutMs: Int = 20_000,
    ): Any? {
        val params = buildString {
            append("?password=").append(URLEncoder.encode(password, "UTF-8"))
            query.forEach { (k, v) ->
                if (v != null) {
                    append("&").append(k).append("=")
                    append(URLEncoder.encode(v.toString(), "UTF-8"))
                }
            }
        }
        val url = URL("$apiRoot$path$params")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (text.isBlank()) {
                throw ApiException("The server returned nothing (HTTP $code).", code)
            }

            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                // A tunnel that has expired answers with an HTML error page
                // rather than JSON, which is worth saying plainly instead of
                // surfacing a parser error.
                throw ApiException(
                    if (code == 502 || code == 503) {
                        "The server isn't reachable (HTTP $code). If the address ends in " +
                            "trycloudflare.com it has almost certainly expired - those are " +
                            "temporary and change every time the Mac restarts."
                    } else {
                        "The server sent something that isn't JSON (HTTP $code)."
                    },
                    code,
                )
            }

            return unwrap(json, code)
        } finally {
            conn.disconnect()
        }
    }

    /** The `{status, message, data}` envelope, checked and unwrapped. */
    private fun unwrap(json: JSONObject, httpCode: Int): Any? {
        val status = json.optInt("status", httpCode)
        if (httpCode !in 200..299 || status !in 200..299) {
            val message = json.optString("message").ifBlank { "HTTP $httpCode" }
            val detail = json.optJSONObject("error")?.optString("message").orEmpty()
            throw ApiException(if (detail.isBlank()) message else "$message: $detail", status)
        }
        return if (json.isNull("data")) null else json.get("data")
    }

    private fun getObj(path: String, query: Map<String, Any?> = emptyMap()) =
        request("GET", path, query)

    private fun post(path: String, body: JSONObject, query: Map<String, Any?> = emptyMap()) =
        request("POST", path, query, body)

    // --- calls we actually make ----------------------------------------

    /** Cheapest possible liveness check, and the one setup runs first. */
    fun ping(): Boolean = runCatching { request("GET", "/ping", timeoutMs = 10_000) }.isSuccess

    fun serverInfo(): JSONObject? = getObj("/server/info") as? JSONObject

    /**
     * Conversations, newest first.
     *
     * `with` asks the server to inline the participants and the last message
     * so the chat list can be built from one response instead of one request
     * per thread.
     */
    fun chats(limit: Int = 200, offset: Int = 0): JSONArray =
        post(
            "/chat/query",
            JSONObject()
                .put("with", JSONArray(listOf("participants", "lastmessage")))
                .put("limit", limit)
                .put("offset", offset)
                .put("sort", "lastmessage"),
        ) as? JSONArray ?: JSONArray()

    /**
     * One conversation's messages.
     *
     * `after` is a millisecond timestamp, which is what makes polling cheap:
     * once the first page is in, every later poll asks only for what is newer
     * than the newest thing already held.
     */
    fun messages(chatGuid: String, limit: Int = 100, after: Long? = null): JSONArray =
        getObj(
            "/chat/${URLEncoder.encode(chatGuid, "UTF-8")}/message",
            mapOf(
                "with" to "attachment,handle,message.attributedbody",
                "sort" to "DESC",
                "limit" to limit,
                "after" to after,
            ),
        ) as? JSONArray ?: JSONArray()

    /** Messages across every chat newer than [after] - the poll for new mail. */
    fun messagesSince(after: Long, limit: Int = 100): JSONArray =
        post(
            "/message/query",
            JSONObject()
                .put("with", JSONArray(listOf("chats", "attachment", "handle")))
                .put("after", after)
                .put("sort", "DESC")
                .put("limit", limit),
        ) as? JSONArray ?: JSONArray()

    /**
     * Sends text.
     *
     * `tempGuid` is the server's idempotency key, and it is also how the
     * echoed message is recognised when it comes back round through the poll
     * - without it the message you just sent arrives again as though somebody
     * else had sent it.
     */
    fun sendText(
        chatGuid: String,
        tempGuid: String,
        text: String,
        subject: String? = null,
        effectId: String? = null,
        replyToGuid: String? = null,
        privateApi: Boolean = true,
    ): JSONObject? {
        val body = JSONObject()
            .put("chatGuid", chatGuid)
            .put("tempGuid", tempGuid)
            .put("message", text)
            .put("method", if (privateApi) "private-api" else "apple-script")
        if (privateApi) {
            subject?.let { body.put("subject", it) }
            effectId?.let { body.put("effectId", it) }
            replyToGuid?.let { body.put("selectedMessageGuid", it).put("partIndex", 0) }
        }
        return post("/message/text", body) as? JSONObject
    }

    fun react(chatGuid: String, messageGuid: String, messageText: String, reaction: String) {
        post(
            "/message/react",
            JSONObject()
                .put("chatGuid", chatGuid)
                .put("selectedMessageGuid", messageGuid)
                .put("selectedMessageText", messageText)
                .put("reaction", reaction)
                .put("partIndex", 0),
        )
    }

    fun unsend(messageGuid: String) {
        post("/message/${URLEncoder.encode(messageGuid, "UTF-8")}/unsend", JSONObject().put("partIndex", 0))
    }

    fun edit(messageGuid: String, newText: String) {
        post(
            "/message/${URLEncoder.encode(messageGuid, "UTF-8")}/edit",
            JSONObject()
                .put("editedMessage", newText)
                .put("backwardsCompatMessage", "Edited to “$newText”")
                .put("partIndex", 0),
        )
    }

    fun markRead(chatGuid: String) {
        post("/chat/${URLEncoder.encode(chatGuid, "UTF-8")}/read", JSONObject())
    }

    fun markUnread(chatGuid: String) {
        post("/chat/${URLEncoder.encode(chatGuid, "UTF-8")}/unread", JSONObject())
    }

    /** Typing indicators are a private-API feature; a server without it 405s. */
    fun setTyping(chatGuid: String, typing: Boolean) {
        val path = "/chat/${URLEncoder.encode(chatGuid, "UTF-8")}/typing"
        runCatching { request(if (typing) "POST" else "DELETE", path, body = JSONObject()) }
            .onFailure { Log.d(TAG, "typing indicator unavailable: ${it.message}") }
    }

    /**
     * Sends a file.
     *
     * Multipart by hand, because this is the one request that isn't JSON and
     * adding a client library for a single endpoint would cost more than the
     * forty lines it saves. The file is streamed rather than read into memory:
     * a video picked from the camera roll is routinely larger than the heap a
     * phone will hand a single app.
     */
    fun sendAttachment(
        chatGuid: String,
        tempGuid: String,
        file: java.io.File,
        fileName: String,
        mimeType: String,
        timeoutMs: Int = 120_000,
    ): JSONObject? {
        val boundary = "----relay${System.nanoTime()}"
        val url = URL(
            "$apiRoot/message/attachment?password=" + URLEncoder.encode(password, "UTF-8"),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            // Without this the whole file is buffered in memory before a byte
            // is sent, which is how a large video becomes an OutOfMemoryError
            // instead of a message.
            setChunkedStreamingMode(64 * 1024)
        }

        try {
            conn.outputStream.buffered().use { out ->
                fun field(name: String, value: String) {
                    out.write(
                        ("--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                            "$value\r\n").toByteArray(),
                    )
                }
                field("chatGuid", chatGuid)
                field("tempGuid", tempGuid)
                field("name", fileName)
                field("method", "private-api")

                out.write(
                    ("--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"attachment\"; " +
                        "filename=\"$fileName\"\r\n" +
                        "Content-Type: $mimeType\r\n\r\n").toByteArray(),
                )
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (text.isBlank()) throw ApiException("The server returned nothing (HTTP $code).", code)
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("The server sent something that isn't JSON (HTTP $code).", code)
            }
            return unwrap(json, code) as? JSONObject
        } finally {
            conn.disconnect()
        }
    }

    fun newChat(addresses: List<String>, message: String?): JSONObject? =
        post(
            "/chat/new",
            JSONObject()
                .put("addresses", JSONArray(addresses))
                .put("message", message ?: "")
                .put("method", "private-api"),
        ) as? JSONObject

    /** The URL an attachment's bytes can be fetched from, password included. */
    fun attachmentUrl(guid: String): String =
        "$apiRoot/attachment/${URLEncoder.encode(guid, "UTF-8")}/download" +
            "?password=" + URLEncoder.encode(password, "UTF-8")

    companion object {
        private const val TAG = "BlueBubbles"
    }
}
