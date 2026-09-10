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

/** What a bubble should do with an attachment, derived from its MIME type. */
enum class MediaKind { IMAGE, VIDEO, AUDIO, FILE }

@Immutable
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    /**
     * Where the bytes actually live - a content:// or file:// URI.
     *
     * Null for the seeded sample attachments, which have no file behind them
     * and render as placeholders. Anything you attach yourself carries a real
     * URI and is loaded, played, opened and saved for real.
     */
    val uri: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    /** Speech-to-text for a voice message, when the device could produce it. */
    val transcript: String? = null,
) {
    val kind: MediaKind
        get() = when {
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            mimeType.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.FILE
        }

    val isImage: Boolean get() = kind == MediaKind.IMAGE

    /** Image and video both render as a tappable thumbnail. */
    val isVisual: Boolean get() = kind == MediaKind.IMAGE || kind == MediaKind.VIDEO
}

/**
 * A poll attached to a message.
 *
 * Votes are stored per option as the set of people who chose it, rather than
 * as counts. Counts cannot answer "did I already vote", cannot be changed
 * without double-counting, and cannot show you who picked what - and all
 * three are things a group chat asks within about ten seconds of a poll
 * appearing.
 */
@Immutable
data class Poll(
    val question: String,
    val options: List<PollOption>,
    val allowsMultiple: Boolean = false,
    val closesAt: Long? = null,
) {
    val totalVotes: Int get() = options.sumOf { it.voters.size }
    val isClosed: Boolean get() = closesAt != null && closesAt < System.currentTimeMillis()
}

@Immutable
data class PollOption(
    val id: String,
    val label: String,
    val voters: List<String> = emptyList(),
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
    /** Saved to your bookmarks. Private - the sender never learns. */
    val isBookmarked: Boolean = false,
    /** Pinned to the conversation's board. */
    val isPinned: Boolean = false,
    /**
     * A note only you can see, attached to somebody else's message.
     *
     * The whole point is that nothing is sent: it's for the context you'd
     * otherwise keep in your head or in a separate app - what a price was
     * before they changed it, which of two plans this was about.
     */
    val note: String? = null,
    /** When to be reminded about this message. */
    val remindAt: Long? = null,
    /** Set while a message is queued to send later, cleared when it goes. */
    val scheduledFor: Long? = null,
    val poll: Poll? = null,
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
    val isArchived: Boolean = false,
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
    /** Group threads show the sender's avatar beside the last bubble of a run. */
    val showAvatar: Boolean = false,
    /**
     * How many replies hang off this message.
     *
     * Replies are pulled out of the transcript entirely and live in the
     * thread view; the original carries the count instead.
     */
    val replyCount: Int = 0,
)
