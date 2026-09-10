//! The wire types between Rust and Kotlin.
//!
//! These are deliberately flat and lossy compared to rustpush's own types.
//! rustpush models the whole iMessage protocol - dozens of message variants,
//! MMCS attachment transfer, poster kits, shared albums. The app only needs the
//! handful of events that change what's on screen, so everything else collapses
//! into `Unhandled` rather than crossing the FFI boundary and being ignored on
//! the other side.

/// Anything that can go wrong on the Rust side, flattened to one variant.
///
/// UniFFI turns this into a Kotlin exception. The distinctions rustpush draws
/// between its error kinds don't change what the app does (every one of them
/// ends as "this didn't work, here's why"), so carrying the message across is
/// enough and keeps the generated bindings small.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("{message}")]
    Failure { message: String },
}

impl CoreError {
    pub fn new(message: impl std::fmt::Display) -> Self {
        CoreError::Failure { message: message.to_string() }
    }
}

/// Where a login attempt stopped.
///
/// Apple's login is a state machine, not a single call: a correct password very
/// often lands on a 2FA step rather than on success. The app has to render a
/// different screen for each outcome, so the step is the return value instead of
/// an error.
#[derive(Debug, Clone, uniffi::Enum)]
pub enum LoginStep {
    /// Password accepted and no second factor needed.
    Complete,
    /// A six-digit code was pushed to the account's trusted devices.
    NeedsDeviceCode,
    /// The account is set up for SMS second factor instead of device codes.
    NeedsSmsCode { phone_numbers: Vec<PhoneNumber> },
    /// Apple wants something done in a browser (terms, account repair).
    NeedsWebStep { url: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PhoneNumber {
    pub id: u32,
    pub number: String,
}

/// One attachment on an incoming or outgoing message.
///
/// `local_path` is empty on incoming messages until the attachment is
/// downloaded - iMessage sends the metadata inline and the bytes separately
/// through MMCS, so the row exists before the file does.
#[derive(Debug, Clone, uniffi::Record)]
pub struct AttachmentInfo {
    pub name: String,
    pub mime_type: String,
    pub size_bytes: u64,
    pub local_path: String,
}

/// Everything the app can be told about a conversation by an incoming message.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ConversationInfo {
    pub participants: Vec<String>,
    pub group_name: Option<String>,
    /// iMessage's own identifier for a group, stable across renames.
    pub sender_guid: Option<String>,
}

/// What actually happened, once the protocol noise is stripped out.
#[derive(Debug, Clone, uniffi::Enum)]
pub enum EventKind {
    Text {
        text: String,
        subject: Option<String>,
        /// GUID of the message being replied to, if this is a reply.
        reply_to_id: Option<String>,
        /// A screen or bubble effect identifier, e.g. `com.apple.MobileSMS.expressivesend.impact`.
        effect: Option<String>,
        attachments: Vec<AttachmentInfo>,
        /// True for SMS relayed through a paired iPhone rather than iMessage.
        is_sms: bool,
    },
    /// A tapback. `reaction` is the emoji itself for emoji tapbacks, or one of
    /// the six classic names (heart/like/dislike/laugh/emphasize/question).
    Tapback {
        target_id: String,
        target_part: u64,
        reaction: String,
        added: bool,
    },
    Edit { target_id: String, target_part: u64, new_text: String },
    Unsend { target_id: String, target_part: u64 },
    Typing { active: bool },
    /// The other side read the conversation.
    Read,
    /// The other side's device acknowledged receipt.
    Delivered,
    /// The other side marked the conversation unread.
    MarkedUnread,
    GroupRenamed { name: String },
    ParticipantsChanged { participants: Vec<String> },
    /// A send failed after the fact. `code` is Apple's own status number.
    SendFailed { target_id: String, code: u64 },
    /// A recognised protocol message the app has no screen for yet. Kept as a
    /// variant rather than dropped so the receive loop stays lossless and the
    /// Kotlin side can log what it saw.
    Unhandled { description: String },
}

/// A single event from the push connection, addressed to a conversation.
#[derive(Debug, Clone, uniffi::Record)]
pub struct IncomingEvent {
    /// The message GUID. For tapbacks and edits this is the GUID of the
    /// tapback itself, not of what it points at.
    pub id: String,
    pub sender: Option<String>,
    pub conversation: ConversationInfo,
    /// Apple sends nanoseconds since the epoch; this is milliseconds, which is
    /// what Kotlin wants.
    pub timestamp_ms: u64,
    pub kind: EventKind,
    /// True when the sender's identity couldn't be verified against IDS.
    pub verification_failed: bool,
}

/// The identities this account can send from - an Apple ID address plus any
/// phone numbers and aliases registered to it.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Handles {
    pub all: Vec<String>,
    pub preferred: Option<String>,
}

/// How the Rust side reaches the app.
///
/// The push connection is long-lived and delivers messages whenever Apple has
/// one, which doesn't fit a request/response call. UniFFI's callback interfaces
/// let the receive loop push into Kotlin directly.
#[uniffi::export(with_foreign)]
pub trait EventListener: Send + Sync {
    fn on_event(&self, event: IncomingEvent);
    /// Called whenever the registration state changes and needs persisting.
    /// Losing this means re-registering with Apple on next launch, which burns
    /// a registration slot, so the app writes it out immediately.
    fn on_state_changed(&self);
    /// The push connection dropped. Reconnection is automatic; this exists so
    /// the UI can show it.
    fn on_connection_lost(&self, reason: String);
}
