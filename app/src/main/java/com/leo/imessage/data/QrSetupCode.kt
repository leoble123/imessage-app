package com.leo.imessage.data

import org.json.JSONArray
import org.json.JSONObject

/** What a scanned setup QR code resolved to. */
sealed interface QrSetupPayload {
    /** A BlueBubbles-compatible server: address and server password. */
    data class MacServer(val url: String, val password: String) : QrSetupPayload

    /** This fork's own relay: the host and pairing code a registration server prints. */
    data class Relay(val host: String, val code: String) : QrSetupPayload
}

/**
 * Parses whatever a QR code scanned on the setup screen decoded to.
 *
 * Three shapes are understood:
 *
 *  - `["password","https://host"]` - the two-element JSON array OpenBubbles'
 *    own server QR encodes (see `server_credentials.dart` /
 *    `qr_code_scanner.dart` upstream). Recognising it means a BlueBubbles
 *    server's existing QR works here unchanged, nothing new to generate.
 *  - `{"host":"...","code":"..."}` - this fork's relay pairing code, for a
 *    self-hosted registration server printing its own QR. A handful of key
 *    names are accepted as aliases so the server doesn't have to match one
 *    exact schema, and the same object shape with `url`/`password` instead is
 *    read as a Mac server.
 *  - `host|code` - a plain-text fallback for a relay operator with no JSON on
 *    hand: whatever's before the first `|` is the host, the rest is the code.
 *
 * Nothing here logs the payload - it's exactly the token/password/pairing
 * code the security rules say never to write to the log.
 */
object QrSetupCode {

    fun parse(raw: String): QrSetupPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("[")) return parseArray(trimmed)
        if (trimmed.startsWith("{")) return parseObject(trimmed)

        val bar = trimmed.indexOf('|')
        if (bar > 0 && bar < trimmed.length - 1) {
            val host = trimmed.substring(0, bar).trim()
            val code = trimmed.substring(bar + 1).trim()
            if (host.isNotEmpty() && code.isNotEmpty()) return QrSetupPayload.Relay(host, code)
        }

        return null
    }

    private fun parseArray(raw: String): QrSetupPayload? {
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        if (array.length() < 2) return null
        val password = array.optString(0).takeIf { it.isNotBlank() } ?: return null
        val url = array.optString(1).takeIf { it.isNotBlank() } ?: return null
        return QrSetupPayload.MacServer(url = url, password = password)
    }

    private fun parseObject(raw: String): QrSetupPayload? {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null

        val host = obj.firstNonBlank("host", "relayHost", "relay_host")
        val code = obj.firstNonBlank("code", "pairingCode", "pairing_code", "relayCode")
        if (host != null && code != null) return QrSetupPayload.Relay(host, code)

        val url = obj.firstNonBlank("url", "server", "address")
        val password = obj.firstNonBlank("password", "pass")
        if (url != null && password != null) return QrSetupPayload.MacServer(url, password)

        return null
    }

    private fun JSONObject.firstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }
}
