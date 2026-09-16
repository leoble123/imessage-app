package com.leo.imessage.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turning BlueBubbles' JSON into this app's models.
 *
 * Kept apart from the transport on purpose: this is the part with opinions in
 * it, and the part worth testing. Everything here is pure - JSON in, models
 * out, no network - so it can be exercised from unit tests against captured
 * payloads rather than against a Mac that has to be awake.
 */
object BlueBubblesMapping {

    /** BlueBubbles' group marker. Anything else is a one-to-one thread. */
    private const val CHAT_STYLE_GROUP = 43

    fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    // --- handles --------------------------------------------------------

    fun contact(handle: JSONObject?): Contact? {
        if (handle == null) return null
        val address = handle.optString("address").orEmpty()
        if (address.isBlank()) return null
        // The server may know a name from the Mac's address book; fall back to
        // the raw address, which the UI already knows how to make presentable.
        val name = handle.optString("displayName").ifBlank {
            handle.optJSONObject("contact")?.optString("displayName").orEmpty()
        }
        return Contact(
            id = address,
            displayName = name.ifBlank { address },
            handle = address,
        )
    }

    fun participants(chat: JSONObject): List<Contact> =
        chat.optJSONArray("participants")?.objects()?.mapNotNull { contact(it) }.orEmpty()

    // --- chats ----------------------------------------------------------

    fun chat(json: JSONObject, existing: Chat? = null): Chat? {
        val guid = json.optString("guid").ifBlank { return null }
        val people = participants(json)
        val group = json.optInt("style") == CHAT_STYLE_GROUP || people.size > 1

        val title = json.optString("displayName").ifBlank {
            if (group) people.joinToString(", ") { it.displayName }
            else people.firstOrNull()?.displayName ?: json.optString("chatIdentifier")
        }

        return Chat(
            id = guid,
            displayName = title.ifBlank { guid },
            participants = people,
            // Filled in by the caller from the messages it just stored; the
            // chat payload's own lastMessage is a different shape and not
            // worth a second mapping path.
            lastMessage = existing?.lastMessage,
            unreadCount = existing?.unreadCount ?: 0,
            isPinned = existing?.isPinned ?: false,
            isMuted = existing?.isMuted ?: false,
            isArchived = json.optBoolean("isArchived", existing?.isArchived ?: false),
            service = if (guid.startsWith("SMS;")) Service.SMS else Service.IMESSAGE,
        )
    }

    // --- messages -------------------------------------------------------

    /**
     * Apple's epoch, not Unix.
     *
     * Some fields come back in milliseconds since 1970 and some in the Core
     * Data epoch (2001-01-01). A value small enough to predate 2001 in Unix
     * terms is the latter, and shifting it is the difference between a
     * conversation in order and one dated 1970.
     */
    private const val APPLE_EPOCH_MS = 978_307_200_000L

    fun timestamp(raw: Long): Long = when {
        raw <= 0L -> 0L
        raw < APPLE_EPOCH_MS -> raw + APPLE_EPOCH_MS
        else -> raw
    }

    /** iMessage's own names for tapbacks, and the ones this app uses. */
    fun tapbackKind(associatedType: Int): TapbackKind? = when (associatedType) {
        2000 -> TapbackKind.HEART
        2001 -> TapbackKind.THUMBS_UP
        2002 -> TapbackKind.THUMBS_DOWN
        2003 -> TapbackKind.HAHA
        2004 -> TapbackKind.EXCLAIM
        2005 -> TapbackKind.QUESTION
        else -> null
    }

    fun reactionName(kind: TapbackKind): String = when (kind) {
        TapbackKind.HEART -> "love"
        TapbackKind.THUMBS_UP -> "like"
        TapbackKind.THUMBS_DOWN -> "dislike"
        TapbackKind.HAHA -> "laugh"
        TapbackKind.EXCLAIM -> "emphasize"
        TapbackKind.QUESTION -> "question"
        TapbackKind.ANY_EMOJI -> "love"
    }

    fun effect(styleId: String?): MessageEffect = when {
        styleId.isNullOrBlank() -> MessageEffect.NONE
        styleId.endsWith("impact") -> MessageEffect.SLAM
        styleId.endsWith("loud") -> MessageEffect.LOUD
        styleId.endsWith("gentle") -> MessageEffect.GENTLE
        styleId.endsWith("invisibleink") -> MessageEffect.INVISIBLE_INK
        else -> MessageEffect.NONE
    }

    fun mediaKind(mime: String?): MediaKind = when {
        mime == null -> MediaKind.FILE
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime.startsWith("audio/") -> MediaKind.AUDIO
        else -> MediaKind.FILE
    }

    fun attachments(json: JSONObject, urlFor: (String) -> String): List<Attachment> =
        json.optJSONArray("attachments")?.objects()?.mapNotNull { a ->
            val guid = a.optString("guid").ifBlank { return@mapNotNull null }
            val mime = a.optString("mimeType").ifBlank { "application/octet-stream" }
            Attachment(
                id = guid,
                fileName = a.optString("transferName").ifBlank { "attachment" },
                mimeType = mime,
                uri = urlFor(guid),
                sizeBytes = a.optLong("totalBytes").takeIf { it > 0 },
            )
        }.orEmpty()

    /**
     * Delivery state, in the order the receipts actually arrive.
     *
     * Checked most-advanced first: a read message also has a delivered date,
     * so testing for delivery before reading would report every read message
     * as merely delivered.
     */
    fun deliveryState(json: JSONObject): DeliveryState {
        if (json.optInt("error", 0) != 0) return DeliveryState.FAILED
        if (!json.optBoolean("isFromMe", false)) return DeliveryState.DELIVERED
        return when {
            json.optLong("dateRead", 0L) > 0L -> DeliveryState.READ
            json.optLong("dateDelivered", 0L) > 0L -> DeliveryState.DELIVERED
            json.optLong("dateCreated", 0L) > 0L -> DeliveryState.SENT
            else -> DeliveryState.SENDING
        }
    }

    /**
     * One message, or null when it isn't one.
     *
     * Tapbacks and retractions arrive through the same endpoint as ordinary
     * messages, distinguished only by associatedMessageType. They are handled
     * by the caller, which has to attach them to the message they point at
     * rather than add them to the transcript as messages in their own right.
     */
    fun message(json: JSONObject, chatId: String, urlFor: (String) -> String): Message? {
        val guid = json.optString("guid").ifBlank { return null }
        val retracted = json.optLong("dateRetracted", 0L) > 0L
        val text = json.optString("text").orEmpty().trimEnd('￼')

        return Message(
            id = guid,
            chatId = chatId,
            text = if (retracted) "" else text,
            timestamp = timestamp(json.optLong("dateCreated", 0L)),
            isFromMe = json.optBoolean("isFromMe", false),
            senderId = json.optJSONObject("handle")?.optString("address"),
            service = if (chatId.startsWith("SMS;")) Service.SMS else Service.IMESSAGE,
            deliveryState = deliveryState(json),
            attachments = attachments(json, urlFor),
            effect = effect(json.optString("expressiveSendStyleId").takeIf { it.isNotBlank() }),
            replyToId = json.optString("threadOriginatorGuid").takeIf { it.isNotBlank() },
            unsentText = if (retracted) text.ifBlank { null } else null,
            editedAt = json.optLong("dateEdited", 0L).takeIf { it > 0L }?.let { timestamp(it) },
        )
    }

    /** True when this payload is a tapback rather than a message of its own. */
    fun isTapback(json: JSONObject): Boolean =
        json.optInt("associatedMessageType", 0) in 2000..3999

    /** The message a tapback points at, with its "p:0/" prefix removed. */
    fun tapbackTarget(json: JSONObject): String? =
        json.optString("associatedMessageGuid")
            .takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')

    /** Tapbacks above 3000 are removals of the matching one below it. */
    fun tapbackRemoved(json: JSONObject): Boolean =
        json.optInt("associatedMessageType", 0) >= 3000
}
