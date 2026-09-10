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
///
/// The field is `reason` rather than `message` on purpose: UniFFI turns error
/// variants into Kotlin exception subclasses, and a field called `message`
/// collides with `Throwable.message`, which fails to compile on the Kotlin
/// side with an overload ambiguity that points at generated code.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("{reason}")]
    Failure { reason: String },
}

impl CoreError {
    pub fn new(reason: impl std::fmt::Display) -> Self {
        CoreError::Failure { reason: reason.to_string() }
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

/// A file on its way out.
#[derive(Debug, Clone, uniffi::Record)]
pub struct OutgoingFile {
    /// A real filesystem path. Content URIs have to be copied out first -
    /// the Rust side opens this with the ordinary file API.
    pub path: String,
    pub name: String,
    pub mime_type: String,
    /// Apple's own type identifier, e.g. `public.jpeg`. Recipients use it to
    /// decide how to present the file, so a wrong one shows a photo as a
    /// generic document.
    pub uti_type: String,
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
        /// Which part of the message the text sits in.
        ///
        /// A message is a list of parts - attachments, then text. Tapbacks,
        /// edits and unsends all address a specific one, so reacting to a
        /// photo-plus-caption while assuming part zero puts the tapback on
        /// the photo.
        text_part: u32,
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

/// What happened to a call.
///
/// FaceTime is a separate service from iMessage with its own push topics, so
/// these arrive on the same connection but through a different client and are
/// reported separately rather than being folded into message events.
#[derive(Debug, Clone, uniffi::Enum)]
pub enum CallEvent {
    /// Someone is calling. This is the one that has to ring the phone.
    Incoming {
        call_id: String,
        /// Everyone on the call, us included.
        members: Vec<String>,
        is_video: bool,
    },
    /// Our outgoing call started ringing on the other end.
    Ringing { call_id: String },
    /// Someone joined - the call is live.
    Joined { call_id: String, handle: String },
    /// Someone left. A one-to-one call is over when this arrives.
    Left { call_id: String, handle: String },
    Declined { call_id: String },
    /// Answered on another one of the account's devices, so stop ringing here.
    AnsweredElsewhere { call_id: String },
    /// Media path is up.
    Connected { call_id: String },
    Disconnected { call_id: String },
    /// The call's shareable link changed.
    LinkChanged { call_id: String, link: String },
}

/// A call as the app should show it.
#[derive(Debug, Clone, uniffi::Record)]
pub struct CallInfo {
    pub call_id: String,
    pub members: Vec<String>,
    pub is_video: bool,
    /// True when we started it.
    pub outgoing: bool,
    /// Milliseconds since the epoch, for the call duration timer.
    pub started_ms: u64,
}

/// How the Rust side reaches the app.
///
/// The push connection is long-lived and delivers messages whenever Apple has
/// one, which doesn't fit a request/response call. UniFFI's callback interfaces
/// let the receive loop push into Kotlin directly.
#[uniffi::export(with_foreign)]
pub trait EventListener: Send + Sync {
    fn on_event(&self, event: IncomingEvent);
    /// A FaceTime call changed state.
    fn on_call_event(&self, event: CallEvent);

    /// One encoded audio frame from the far end, ready to decode and play.
    ///
    /// These arrive on the media thread at the codec's frame rate - roughly
    /// fifty a second - so the Kotlin side must hand off rather than block.
    fn on_audio_frame(&self, frame: Vec<u8>, timestamp: u32);

    /// The far end's decoder configuration, sent before its first frame.
    ///
    /// For AAC this is the AudioSpecificConfig, which is exactly what a
    /// decoder needs as its csd-0. Taking it off the wire rather than assuming
    /// a sample rate is what makes decoding reliable instead of a guess.
    fn on_audio_config(&self, config: Vec<u8>);
    /// Called whenever the registration state changes and needs persisting.
    /// Losing this means re-registering with Apple on next launch, which burns
    /// a registration slot, so the app writes it out immediately.
    fn on_state_changed(&self);
    /// The push connection dropped. Reconnection is automatic; this exists so
    /// the UI can show it.
    fn on_connection_lost(&self, reason: String);
}
