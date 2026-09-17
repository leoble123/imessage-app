package com.leo.imessage.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** What a scanned or pasted setup code resolved to. */
sealed interface QrSetupPayload {
    /** A BlueBubbles-compatible server: address and server password. */
    data class MacServer(val url: String, val password: String) : QrSetupPayload

    /**
     * A registration relay: host, pairing code, and the access token a public
     * relay (Beeper's) needs. A self-hosted relay takes no token.
     */
    data class Relay(
        val host: String,
        val code: String,
        val token: String? = null,
    ) : QrSetupPayload

    /**
     * An OpenAbsinthe hardware export - the OABS blob a Mac's exporter
     * produces, base64 as the core takes it.
     *
     * Recognised rather than supported: see [AccountManager.configureHardware]
     * for why one of these cannot be signed in with from a phone. Reading it
     * and saying so beats a scan that silently does nothing.
     */
    data class MacHardware(val base64: String) : QrSetupPayload
}

/**
 * Parses whatever a setup code - scanned or pasted - turned out to be.
 *
 * The shapes understood, all of them ones something real already emits:
 *
 *  - `["password","https://host"]` - the two-element JSON array OpenBubbles'
 *    own server QR encodes (see `server_credentials.dart` upstream). A
 *    BlueBubbles/OpenBubbles server's existing QR works here unchanged.
 *  - `{"host":"...","code":"...","token":"..."}` - a relay, with `token`
 *    optional and a few key aliases accepted, or the same object shape with
 *    `url`/`password` for a Mac server.
 *  - `host|code` or `host|code|token` - a plain-text fallback for a relay
 *    operator with no JSON tooling on hand.
 *  - An OABS blob - base64 when pasted, raw bytes when scanned. OpenBubbles'
 *    Mac exporter emits these, and its QR carries them as raw bytes rather
 *    than text, which is why [parseScan] takes bytes at all.
 *
 * Nothing here logs the payload - it's a server password, a pairing code or a
 * machine's identity, whichever shape it arrived in.
 */
object QrSetupCode {

    /** OpenAbsinthe's tag: four ASCII bytes at the front of every export. */
    private val OABS_MAGIC = byteArrayOf('O'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte())

    /**
     * A scan result, which may be text, bytes, or both.
     *
     * ML Kit hands back a null `rawValue` for any payload that isn't valid
     * UTF-8, which every OABS blob is - so a scanner reading only the text
     * form sees nothing at all for exactly the code this is most needed for.
     */
    fun parseScan(text: String?, bytes: ByteArray?): QrSetupPayload? {
        if (bytes != null && bytes.hasOabsMagic()) {
            return QrSetupPayload.MacHardware(Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        if (!text.isNullOrBlank()) return parse(text)
        if (bytes != null) {
            val decoded = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            if (decoded != null) return parse(decoded)
        }
        return null
    }

    fun parse(text: String): QrSetupPayload? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("[")) return parseArray(trimmed)
        if (trimmed.startsWith("{")) return parseObject(trimmed)
        if (trimmed.contains('|')) return parsePipes(trimmed)

        return parseHardwareBlob(trimmed)
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
        if (host != null && code != null) {
            return QrSetupPayload.Relay(host, code, obj.firstNonBlank("token", "beeperToken", "beeper_token"))
        }

        val url = obj.firstNonBlank("url", "server", "address")
        val password = obj.firstNonBlank("password", "pass")
        if (url != null && password != null) return QrSetupPayload.MacServer(url, password)

        return null
    }

    private fun parsePipes(raw: String): QrSetupPayload? {
        val parts = raw.split('|').map { it.trim() }
        if (parts.size < 2) return null
        val host = parts[0].takeIf { it.isNotEmpty() } ?: return null
        val code = parts[1].takeIf { it.isNotEmpty() } ?: return null
        val token = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        return QrSetupPayload.Relay(host, code, token)
    }

    /**
     * A pasted OABS export: base64, usually with the line breaks whatever
     * copied it left in.
     */
    private fun parseHardwareBlob(raw: String): QrSetupPayload? {
        val cleaned = raw.filterNot(Char::isWhitespace)
        if (cleaned.isEmpty()) return null
        val bytes = runCatching { Base64.decode(cleaned, Base64.DEFAULT) }.getOrNull() ?: return null
        if (!bytes.hasOabsMagic()) return null
        return QrSetupPayload.MacHardware(cleaned)
    }

    private fun ByteArray.hasOabsMagic(): Boolean =
        size >= 6 && OABS_MAGIC.indices.all { this[it] == OABS_MAGIC[it] }

    private fun JSONObject.firstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }
}
