//! Translating rustpush's protocol types into the app's flat wire types.

use rustpush::{
    Attachment, ConversationData, Message, MessageInst, MessageParts, MessagePart,
    MessageType, Reaction, ReactMessageType,
};

use crate::types::{AttachmentInfo, ConversationInfo, EventKind, IncomingEvent};

/// Apple timestamps are nanoseconds since the Unix epoch; Kotlin works in
/// milliseconds everywhere (System.currentTimeMillis, Instant.ofEpochMilli).
fn to_millis(apple_nanos: u64) -> u64 {
    apple_nanos / 1_000_000
}

/// The six built-in tapbacks keep their iMessage names so the Kotlin side can
/// map them onto the app's own TapbackKind enum; emoji tapbacks come across as
/// the emoji itself, which is exactly what gets rendered.
fn reaction_name(reaction: &Reaction) -> String {
    match reaction {
        Reaction::Heart => "heart".to_string(),
        Reaction::Like => "like".to_string(),
        Reaction::Dislike => "dislike".to_string(),
        Reaction::Laugh => "laugh".to_string(),
        Reaction::Emphasize => "emphasize".to_string(),
        Reaction::Question => "question".to_string(),
        Reaction::Emoji(emoji) => emoji.clone(),
        Reaction::Sticker { .. } => "sticker".to_string(),
    }
}

fn attachment_info(attachment: &Attachment) -> AttachmentInfo {
    AttachmentInfo {
        name: attachment.name.clone(),
        mime_type: attachment.mime.clone(),
        size_bytes: attachment.get_size() as u64,
        // Incoming attachments arrive as MMCS references, not bytes. The file
        // only exists once it's fetched, which is a separate call.
        local_path: String::new(),
    }
}

fn attachments_of(parts: &MessageParts) -> Vec<AttachmentInfo> {
    parts
        .0
        .iter()
        .filter_map(|p| match &p.part {
            MessagePart::Attachment(a) => Some(attachment_info(a)),
            _ => None,
        })
        .collect()
}

fn conversation_info(conversation: Option<&ConversationData>) -> ConversationInfo {
    match conversation {
        Some(c) => ConversationInfo {
            participants: c.participants.clone(),
            group_name: c.cv_name.clone(),
            sender_guid: c.sender_guid.clone(),
        },
        // Delivery and read receipts sometimes arrive without conversation
        // data attached; the GUID they target is enough to place them.
        None => ConversationInfo {
            participants: vec![],
            group_name: None,
            sender_guid: None,
        },
    }
}

/// Collapses a rustpush message into the one event the app cares about.
pub fn to_event(msg: &MessageInst) -> IncomingEvent {
    let kind = match &msg.message {
        Message::Message(normal) => EventKind::Text {
            text: normal.parts.raw_text(),
            subject: normal.subject.clone(),
            reply_to_id: normal.reply_guid.clone(),
            effect: normal.effect.clone(),
            attachments: attachments_of(&normal.parts),
            is_sms: matches!(normal.service, MessageType::SMS { .. }),
        },
        Message::React(react) => match &react.reaction {
            ReactMessageType::React { reaction, enable } => EventKind::Tapback {
                target_id: react.to_uuid.clone(),
                target_part: react.to_part.unwrap_or(0),
                reaction: reaction_name(reaction),
                added: *enable,
            },
            // An "extension" reaction is a sticker or an iMessage app payload
            // dropped onto a bubble. There's no UI for those yet, but naming it
            // keeps the log readable instead of showing a bare "unknown".
            ReactMessageType::Extension { .. } => EventKind::Unhandled {
                description: format!("sticker or app payload on {}", react.to_uuid),
            },
        },
        Message::Edit(edit) => EventKind::Edit {
            target_id: edit.tuuid.clone(),
            target_part: edit.edit_part,
            new_text: edit.new_parts.raw_text(),
        },
        Message::Unsend(unsend) => EventKind::Unsend {
            target_id: unsend.tuuid.clone(),
            target_part: unsend.edit_part,
        },
        Message::Typing(active, _app) => EventKind::Typing { active: *active },
        Message::Read | Message::MessageReadOnDevice => EventKind::Read,
        Message::Delivered => EventKind::Delivered,
        Message::MarkUnread => EventKind::MarkedUnread,
        Message::RenameMessage(rename) => EventKind::GroupRenamed {
            name: rename.new_name.clone(),
        },
        Message::ChangeParticipants(change) => EventKind::ParticipantsChanged {
            participants: change.new_participants.clone(),
        },
        Message::Error(err) => EventKind::SendFailed {
            target_id: err.for_uuid.clone(),
            code: err.status,
        },
        other => EventKind::Unhandled {
            description: format!("{:?}", std::mem::discriminant(other)),
        },
    };

    IncomingEvent {
        id: msg.id.clone(),
        sender: msg.sender.clone(),
        conversation: conversation_info(msg.conversation.as_ref()),
        timestamp_ms: to_millis(msg.sent_timestamp),
        kind,
        verification_failed: msg.verification_failed,
    }
}

/// Maps a FaceTime event onto the app's call model.
///
/// Returns `None` for the ones the app has nothing to do with - link
/// bookkeeping and "let me in" requests from people trying to join a link,
/// which need an approval flow that doesn't exist yet. Dropping them here is
/// deliberate: showing an incoming-call screen for a join request the app
/// can't actually approve would ring for nothing.
pub fn to_call_event(message: &rustpush::facetime::FTMessage) -> Option<crate::types::CallEvent> {
    use crate::types::CallEvent;
    use rustpush::facetime::FTMessage as M;

    Some(match message {
        // Somebody is being added to a call and it is ringing - that's an
        // incoming call as far as the app is concerned.
        M::AddMembers { guid, members, ring } if *ring => CallEvent::Incoming {
            call_id: guid.clone(),
            members: members.iter().map(|m| m.handle.clone()).collect(),
            // The wire doesn't say at ring time; the call screen starts on
            // audio and upgrades if video arrives.
            is_video: false,
        },
        M::Ring { guid } => CallEvent::Ringing { call_id: guid.clone() },
        M::JoinEvent { guid, handle, .. } => CallEvent::Joined {
            call_id: guid.clone(),
            handle: handle.clone(),
        },
        M::LeaveEvent { guid, handle, .. } => CallEvent::Left {
            call_id: guid.clone(),
            handle: handle.clone(),
        },
        M::Decline { guid } => CallEvent::Declined { call_id: guid.clone() },
        M::RespondedElsewhere { guid } => {
            CallEvent::AnsweredElsewhere { call_id: guid.clone() }
        }
        M::Connected { guid } => CallEvent::Connected { call_id: guid.clone() },
        M::Disconnected { guid } => CallEvent::Disconnected { call_id: guid.clone() },
        M::AddMembers { .. } | M::RemoveMembers { .. } | M::LinkChanged { .. }
        | M::LetMeInRequest(_) => return None,
    })
}

/// The attachment references on a message, with their positions.
///
/// Returned as the protocol's own type rather than the app's: the reference
/// carries the decryption key and location, which the flat record has nowhere
/// to put, and which fetching the bytes later depends on.
pub fn attachments_in(msg: &MessageInst) -> Vec<(u32, Attachment)> {
    let Message::Message(normal) = &msg.message else { return vec![] };
    normal
        .parts
        .0
        .iter()
        .filter_map(|p| match &p.part {
            MessagePart::Attachment(a) => Some(a.clone()),
            _ => None,
        })
        .enumerate()
        .map(|(i, a)| (i as u32, a))
        .collect()
}
