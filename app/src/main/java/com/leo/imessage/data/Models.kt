package com.leo.imessage.data

import androidx.compose.runtime.Immutable

@Immutable
data class Contact(
    val id: String,
    val displayName: String,
    val handle: String,
    val initials: String = displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" },
)

enum class Service { IMESSAGE, SMS }

enum class DeliveryState { SENDING, SENT, DELIVERED, READ, FAILED }

/** iMessage's screen/bubble effects. */
enum class MessageEffect { NONE, SLAM, LOUD, GENTLE, INVISIBLE_INK }

/**
 * The six classic tapbacks, plus ANY_EMOJI for iOS 18's arbitrary-emoji
 * reactions. [Tapback.emoji] carries the actual character in that case.
 */
enum class TapbackKind { HEART, THUMBS_UP, THUMBS_DOWN, HAHA, EXCLAIM, QUESTION, ANY_EMOJI }

@Immutable
data class Tapback(
    val kind: TapbackKind,
    val fromMe: Boolean,
    val senderId: String,
    /** Set only when [kind] is ANY_EMOJI. */
    val emoji: String? = null,
)

@Immutable
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val isImage: Boolean = mimeType.startsWith("image/"),
)

@Immutable
data class Message(
    val id: String,
    val chatId: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val senderId: String?,
    val service: Service = Service.IMESSAGE,
    val deliveryState: DeliveryState = DeliveryState.DELIVERED,
    val tapbacks: List<Tapback> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val effect: MessageEffect = MessageEffect.NONE,
    val replyToId: String? = null,
    /** Original text of a message that was unsent, kept for one-tap reveal. */
    val unsentText: String? = null,
    /** Prior versions of an edited message, oldest first. */
    val editHistory: List<String> = emptyList(),
    val isUnsent: Boolean = unsentText != null,
    val editedAt: Long? = null,
) {
    val hasContent: Boolean get() = text.isNotBlank() || attachments.isNotEmpty()
}

@Immutable
data class Chat(
    val id: String,
    val displayName: String,
    val participants: List<Contact>,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isTyping: Boolean = false,
    val service: Service = Service.IMESSAGE,
) {
    val isGroup: Boolean get() = participants.size > 1
    val avatarSeed: String get() = participants.firstOrNull()?.id ?: id
}

/**
 * How messages get visually grouped into runs by the same sender - this
 * drives bubble corner shapes and tail placement, which is most of what makes
 * a thread read as iMessage rather than a generic chat list.
 */
enum class GroupPosition { SINGLE, FIRST, MIDDLE, LAST }

@Immutable
data class MessageRow(
    val message: Message,
    val groupPosition: GroupPosition,
    val showTimestampHeader: Boolean,
    val showSenderName: Boolean,
    /** Only the final delivered/read message in a thread shows its receipt. */
    val showDeliveryReceipt: Boolean,
)
