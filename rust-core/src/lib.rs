//! The iMessage core, exposed to Kotlin.
//!
//! This wraps rustpush - the same protocol implementation OpenBubbles uses - in
//! an API shaped like what the app actually does: log in, register, send, and
//! receive. Everything above this (conversation history, drafts, tapback
//! rendering, the entire UI) lives on the Kotlin side; this owns the network
//! and the cryptographic identity, nothing else.
//!
//! ## Why a relay is required
//!
//! Registering with Apple's IDS needs "validation data" - a blob signed by
//! Apple hardware. rustpush can generate it on macOS (the `macos-validation-data`
//! feature, backed by open-absinthe), but that path is an unimplemented stub in
//! the public tree and cannot work on a phone regardless. The `RelayConfig`
//! path asks a server for it instead, which is how OpenBubbles itself does it.
//! That is why `configure_relay` has to be called before anything else.

uniffi::setup_scaffolding!();

mod convert;
mod state;
mod types;

use std::sync::Arc;

use icloud_auth::{AppleAccount, LoginState};
use keystore::{init_keystore, software::{NoEncryptor, SoftwareKeystore}};
use log::{info, warn};
use omnisette::{default_provider, ArcAnisetteClient, DefaultAnisetteProvider};
use rustpush::{
    authenticate_apple, login_apple_delegates, register, APSConnection,
    APSConnectionResource, ConversationData, IDSNGMIdentity, IDSUser, IMClient,
    LoginDelegate, Message, MessageInst, MessageType, NormalMessage, OSConfig, ReactMessage,
    ReactMessageType, Reaction, RelayConfig, UnsendMessage, EditMessage, MessageParts, PushError,
    MessagePart, IndexedMessagePart, VerifyBody, Attachment, MMCSFile, MADRID_SERVICE,
};
use rustpush::facetime::{FTClient, FACETIME_SERVICE, VIDEO_SERVICE};
use rustpush::avconference::{AudioSender, ChannelFrame, ChannelType, DecoderConfiguration};
use tokio::sync::Mutex;

use state::{read_plist, write_plist, Paths, SavedState};
pub use types::*;

/// The IDS services to register for.
///
/// Madrid is iMessage; the other two are FaceTime's signalling and video
/// services. Registering for a service means telling Apple this device can
/// answer it, so they are only listed now that there is a call screen behind
/// them - an account that advertises FaceTime and never answers rings a
/// caller's phone for nothing.
///
/// This is a function rather than a `static` because rustpush doesn't export
/// the `IDSService` type - only the constants - so the type can't be written
/// down. Returning a reference to a const expression promotes it to 'static,
/// which is the lifetime `register` and `IMClient::new` require.
macro_rules! services {
    () => {
        &[&MADRID_SERVICE, &FACETIME_SERVICE, &VIDEO_SERVICE][..]
    };
}

/// The service names above, for checking an existing registration covers them.
const SERVICE_NAMES: &[&str] = &[
    MADRID_SERVICE.name,
    FACETIME_SERVICE.name,
    VIDEO_SERVICE.name,
];

/// Everything mutable, behind one lock.
///
/// A single mutex rather than one per field: these all change together during
/// login and registration, and the contention that would justify splitting them
/// doesn't exist - the app makes one call at a time.
#[derive(Default)]
struct Inner {
    config: Option<Arc<RelayConfig>>,
    connection: Option<APSConnection>,
    anisette: Option<ArcAnisetteClient<DefaultAnisetteProvider>>,
    account: Option<AppleAccount<DefaultAnisetteProvider>>,
    users: Vec<IDSUser>,
    identity: Option<IDSNGMIdentity>,
    client: Option<Arc<IMClient>>,
    facetime: Option<Arc<FTClient>>,
    /// Kept so the audio path can reach the app too, not just the receive loop.
    listener: Option<Arc<dyn EventListener>>,
    /// The microphone side of a live call, one per call id.
    audio: std::collections::HashMap<String, Arc<Mutex<AudioSender>>>,
    /// Apple hands back a body with the SMS challenge in it, and verifying
    /// the code requires handing that same body back. Only set when the
    /// account went down the SMS path rather than the trusted-device one.
    sms_verify: Option<VerifyBody>,
    /// Set once the receive loop is running, so starting twice is a no-op
    /// rather than two loops racing to consume the same broadcast channel.
    receiving: bool,
}

/// The handle Kotlin holds. One per app process.
#[derive(uniffi::Object)]
pub struct ImessageCore {
    paths: Paths,
    inner: Mutex<Inner>,
    /// Incoming attachments, kept so their bytes can be fetched on demand.
    ///
    /// The reference is all that arrives - it carries the decryption key and
    /// the location, and neither survives being flattened into the app's own
    /// attachment record. Keyed by message and position within it.
    ///
    /// Held here rather than on `Inner` because the receive task needs it and
    /// cannot borrow the object.
    attachments: Arc<Mutex<std::collections::HashMap<(String, u32), Attachment>>>,
}

#[uniffi::export(async_runtime = "tokio")]
impl ImessageCore {
    /// `state_dir` should be the app's private files directory. Everything the
    /// core persists - keys included - goes inside it.
    #[uniffi::constructor]
    pub fn new(state_dir: String) -> Arc<Self> {
        // Route Rust's `log` output to logcat so failures inside rustpush are
        // visible with `adb logcat` instead of vanishing.
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("imessage-core"),
        );

        let paths = Paths::new(state_dir);
        let _ = paths.ensure();

        // The keystore has to exist before the APS handshake, which reaches for
        // it directly. `init_keystore` is a global one-shot, so doing it in the
        // constructor guarantees it happens exactly once and early enough.
        let keystore_path = paths.keystore();
        let write_path = keystore_path.clone();
        init_keystore(SoftwareKeystore {
            state: read_plist(&keystore_path).unwrap_or_default(),
            update_state: Box::new(move |state| {
                if let Err(e) = plist::to_file_xml(&write_path, state) {
                    warn!("failed to persist keystore: {e}");
                }
            }),
            encryptor: NoEncryptor,
        });

        Arc::new(ImessageCore {
            paths,
            inner: Mutex::new(Inner::default()),
            attachments: Arc::new(Mutex::new(std::collections::HashMap::new())),
        })
    }

    /// True when a previous session registered successfully and its state
    /// survived - the app can go straight to the conversation list.
    pub fn is_registered(&self) -> bool {
        read_plist::<SavedState>(&self.paths.registration())
            .map(|s| !s.users.is_empty() && s.users.iter().any(|u| !u.registration.is_empty()))
            .unwrap_or(false)
    }

    /// True when the saved registration predates a service this build needs.
    ///
    /// An account registered before FaceTime was added is registered for
    /// iMessage alone, and Apple will never route a call to it. Nothing about
    /// that is visible from the app - calls simply never arrive - so it has to
    /// be detected and repaired rather than waited on.
    pub fn needs_service_refresh(&self) -> bool {
        let Some(saved) = read_plist::<SavedState>(&self.paths.registration()) else {
            return false;
        };
        if saved.users.is_empty() {
            return false;
        }
        saved.users.iter().any(|u| {
            !u.registration.is_empty()
                && SERVICE_NAMES.iter().any(|name| !u.registration.contains_key(*name))
        })
    }

    /// Points the core at a registration relay and opens the push connection.
    ///
    /// `code` is the pairing code the relay prints; `token` is only used by
    /// Beeper's public relay and is `None` for a self-hosted one.
    pub async fn configure_relay(
        &self,
        host: String,
        code: String,
        token: Option<String>,
    ) -> Result<(), CoreError> {
        // Fetching versions first doubles as a reachability check: a wrong
        // address or a stopped relay fails here, with a clear error, instead of
        // surfacing later as an opaque registration failure.
        let version = RelayConfig::get_versions(&host, &code, &token)
            .await
            .map_err(relay_error)?;

        let saved: Option<SavedState> = read_plist(&self.paths.registration());

        let config = Arc::new(RelayConfig {
            version,
            icloud_ua: "com.apple.iCloudHelper/282 CFNetwork/1408.0.4 Darwin/22.5.0".to_string(),
            aoskit_version: "com.apple.AOSKit/282 (com.apple.accountsd/113)".to_string(),
            // Reusing the device UUID across launches matters: a new one each
            // time looks to Apple like a new device registering, which is what
            // gets an Apple ID rate-limited.
            dev_uuid: self.device_uuid(),
            protocol_version: 1640,
            host,
            code,
            beeper_token: token,
            udid: None,
        });

        let os_config: Arc<dyn OSConfig> = config.clone();
        let (connection, error) =
            APSConnectionResource::new(os_config, saved.as_ref().map(|s| s.push.clone())).await;

        if let Some(error) = error {
            return Err(CoreError::new(format!("push connection failed: {error}")));
        }

        let anisette = default_provider(
            config.get_gsa_config(&*connection.state.read().await, false),
            self.paths.anisette(),
        );

        let mut inner = self.inner.lock().await;
        inner.config = Some(config);
        inner.connection = Some(connection);
        inner.anisette = Some(anisette);
        if let Some(saved) = saved {
            inner.users = saved.users;
            inner.identity = Some(saved.identity);
        }
        Ok(())
    }

    /// Step one of signing in. Returns where Apple stopped, not just success.
    pub async fn login(&self, email: String, password: String) -> Result<LoginStep, CoreError> {
        let mut inner = self.inner.lock().await;
        let (config, connection, anisette) = self.session(&inner)?;

        let account_path = self.paths.account();
        let mut account = AppleAccount::new_with_anisette(
            config.get_gsa_config(&*connection.state.read().await, false),
            anisette,
            read_plist(&account_path),
            Box::new(move |data| {
                if let Err(e) = plist::to_file_xml(&account_path, data) {
                    warn!("failed to persist account: {e}");
                }
            }),
        )
        .map_err(|e| CoreError::new(format!("couldn't start login: {e}")))?;

        // Apple's SRP exchange takes a SHA-256 of the password, never the
        // password itself, so the plaintext never leaves this function.
        let hashed = hash_password(&password);
        let result = account
            .login_email_pass(&email, &hashed)
            .await
            .map_err(|e| CoreError::new(format!("sign-in failed: {e}")))?;

        inner.account = Some(account);
        self.drive_login(&mut inner, result).await
    }

    /// Submits a six-digit code from a trusted device.
    pub async fn submit_device_code(&self, code: String) -> Result<LoginStep, CoreError> {
        let mut inner = self.inner.lock().await;

        // An SMS code is verified against the challenge body Apple issued when
        // it sent the message; a trusted-device code is not. Using the wrong
        // one rejects a perfectly good code.
        let pending = inner.sms_verify.take();
        let account = inner
            .account
            .as_mut()
            .ok_or_else(|| CoreError::new("no sign-in is in progress"))?;

        let result = match pending {
            Some(body) => account.verify_sms_2fa(code, body).await,
            None => account.verify_2fa(code).await,
        }
        .map_err(|e| CoreError::new(format!("That code didn't work: {e}")))?;

        self.drive_login(&mut inner, result).await
    }

    /// Asks Apple to text a code to one of the account's trusted numbers.
    pub async fn request_sms_code(&self, phone_id: u32) -> Result<LoginStep, CoreError> {
        let mut inner = self.inner.lock().await;
        let result = {
            let account = inner
                .account
                .as_ref()
                .ok_or_else(|| CoreError::new("no sign-in is in progress"))?;
            account
                .send_sms_2fa_to_devices(phone_id)
                .await
                .map_err(|e| CoreError::new(format!("Couldn't send the code: {e}")))?
        };
        self.drive_login(&mut inner, result).await
    }

    /// Finishes sign-in: authenticates against IDS and registers this device.
    ///
    /// Only call this once `login`/`submit_device_code` returned `Complete`.
    /// Registration is the expensive, rate-limited step, so it is skipped
    /// entirely when the saved state already carries a valid registration.
    pub async fn complete_registration(&self) -> Result<(), CoreError> {
        let mut inner = self.inner.lock().await;
        let config = inner
            .config
            .clone()
            .ok_or_else(|| CoreError::new("call configure_relay first"))?;
        let connection = inner
            .connection
            .clone()
            .ok_or_else(|| CoreError::new("call configure_relay first"))?;

        if inner.users.is_empty() {
            let account = inner
                .account
                .as_ref()
                .ok_or_else(|| CoreError::new("sign in first"))?;

            let delegates = login_apple_delegates(
                account,
                None,
                config.as_ref() as &dyn OSConfig,
                &[LoginDelegate::IDS],
            )
            .await
            .map_err(|e| CoreError::new(format!("Apple rejected the session: {e}")))?;

            let ids = delegates
                .ids
                .ok_or_else(|| CoreError::new("Apple didn't return an iMessage token"))?;

            let user = authenticate_apple(ids, config.as_ref() as &dyn OSConfig)
                .await
                .map_err(|e| CoreError::new(format!("iMessage authentication failed: {e}")))?;
            inner.users = vec![user];
        }

        let identity = match inner.identity.clone() {
            Some(identity) => identity,
            None => {
                let identity = IDSNGMIdentity::new()
                    .map_err(|e| CoreError::new(format!("couldn't create an identity: {e}")))?;
                inner.identity = Some(identity.clone());
                identity
            }
        };

        // Also re-registers when the saved registration predates a service -
        // an account registered for iMessage alone stays unable to receive
        // FaceTime forever otherwise, with no visible reason why.
        let missing_service = inner.users.iter().any(|u| {
            SERVICE_NAMES.iter().any(|name| !u.registration.contains_key(*name))
        });
        if inner.users.iter().any(|u| u.registration.is_empty()) || missing_service {
            info!("registering with Apple");
            let aps_state = connection.state.read().await.clone();
            register(
                config.as_ref() as &dyn OSConfig,
                &aps_state,
                services!(),
                &mut inner.users,
                &identity,
            )
            .await
            .map_err(|e| CoreError::new(format!("registration failed: {e}")))?;
        }

        self.persist(&mut inner).await?;
        Ok(())
    }

    /// Brings up the message client and starts delivering events to `listener`.
    pub async fn start(&self, listener: Arc<dyn EventListener>) -> Result<(), CoreError> {
        let mut inner = self.inner.lock().await;
        if inner.receiving {
            return Ok(());
        }

        let config = inner
            .config
            .clone()
            .ok_or_else(|| CoreError::new("call configure_relay first"))?;
        let connection = inner
            .connection
            .clone()
            .ok_or_else(|| CoreError::new("call configure_relay first"))?;
        let identity = inner
            .identity
            .clone()
            .ok_or_else(|| CoreError::new("not registered yet"))?;

        if inner.users.is_empty() {
            return Err(CoreError::new("not registered yet"));
        }

        // IDS hands back rotated keys as the session runs. They're part of the
        // registration, so they have to hit disk as they arrive, not at exit -
        // the process can be killed by Android at any moment.
        let registration_path = self.paths.registration();
        let push_for_save = connection.clone();
        let identity_for_save = identity.clone();
        let keys_updated = Box::new(move |updated: Vec<IDSUser>| {
            let path = registration_path.clone();
            let connection = push_for_save.clone();
            let identity = identity_for_save.clone();
            tokio::spawn(async move {
                let push = connection.state.read().await.clone();
                let saved = SavedState { push, users: updated, identity };
                if let Err(e) = write_plist(&path, &saved) {
                    warn!("failed to persist rotated keys: {e}");
                }
            });
        });

        let config_for_ft = config.clone() as Arc<dyn OSConfig>;
        let client = Arc::new(
            IMClient::new(
                connection.clone(),
                inner.users.clone(),
                identity,
                services!(),
                self.paths.id_cache(),
                config as Arc<dyn OSConfig>,
                keys_updated,
            )
            .await,
        );

        // FaceTime is a separate client on the same push connection, with its
        // own topics. It has to exist before the receive loop starts or the
        // first call of the session is missed.
        let facetime_path = self.paths.facetime();
        let save_path = facetime_path.clone();
        let facetime = Arc::new(
            FTClient::new(
                read_plist(&facetime_path).unwrap_or_default(),
                Box::new(move |state| {
                    if let Err(e) = write_plist(&save_path, state) {
                        warn!("failed to persist FaceTime state: {e}");
                    }
                }),
                connection.clone(),
                client.identity.clone(),
                config_for_ft,
            )
            .await,
        );

        inner.client = Some(client.clone());
        inner.facetime = Some(facetime.clone());
        inner.listener = Some(listener.clone());
        inner.receiving = true;
        drop(inner);

        // The receive loop owns its own task for the life of the process. It
        // reads from the push connection's broadcast channel, so it must exist
        // before any message arrives or that message is simply dropped.
        let mut subscription = connection.messages_cont.subscribe();
        let attachment_store = self.attachments.clone();
        tokio::spawn(async move {
            loop {
                let raw = match subscription.recv().await {
                    Ok(raw) => raw,
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(skipped)) => {
                        // Falling behind loses messages outright, so say so
                        // rather than pretending the stream is intact.
                        warn!("dropped {skipped} push messages while behind");
                        continue;
                    }
                    Err(e) => {
                        listener.on_connection_lost(e.to_string());
                        break;
                    }
                };

                // FaceTime claims its own topics. Offering the message there
                // first costs nothing - it returns None for anything that
                // isn't its - and skipping it means calls never arrive.
                match facetime.handle(raw.clone()).await {
                    Ok(Some(event)) => {
                        if let Some(mapped) = convert::to_call_event(&event) {
                            listener.on_call_event(mapped);
                        }
                        continue;
                    }
                    Ok(None) => {}
                    Err(e) => warn!("FaceTime couldn't handle a message: {e}"),
                }

                match client.handle(raw).await {
                    Ok(Some(message)) => {
                        // `has_payload` is false for the protocol's own
                        // bookkeeping traffic, which has nothing to show.
                        if !message.has_payload() {
                            continue;
                        }
                        // Keep the references before the message is
                        // flattened - the app's own record has no room for a
                        // decryption key, and without them a received photo
                        // can never be fetched.
                        {
                            let mut store = attachment_store.lock().await;
                            for (index, attachment) in convert::attachments_in(&message) {
                                store.insert((message.id.clone(), index), attachment);
                            }
                            // Bounded: a long-running session would otherwise
                            // hold every attachment reference it ever saw.
                            if store.len() > 4096 {
                                store.clear();
                            }
                        }
                        listener.on_event(convert::to_event(&message));

                        // Sending the delivery receipt is what makes the other
                        // side's bubble say "Delivered".
                        if let Some(context) = &message.certified_context {
                            let _ = client
                                .identity
                                .certify_delivery("com.apple.madrid", context, false)
                                .await;
                        }
                    }
                    Ok(None) => {}
                    Err(e) => warn!("couldn't decode an incoming message: {e}"),
                }
            }
        });

        Ok(())
    }

    /// The addresses and numbers this account can send from.
    pub async fn handles(&self) -> Result<Handles, CoreError> {
        let inner = self.inner.lock().await;
        let client = inner
            .client
            .as_ref()
            .ok_or_else(|| CoreError::new("not started"))?;
        let all = client.identity.get_handles().await;
        Ok(Handles {
            preferred: all.first().cloned(),
            all,
        })
    }

    /// Sends a text message. Returns the GUID it was sent under, which is what
    /// later tapbacks, edits and unsends have to target.
    pub async fn send_text(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        text: String,
        reply_to_id: Option<String>,
        reply_to_part: Option<String>,
        effect: Option<String>,
    ) -> Result<String, CoreError> {
        let mut normal = NormalMessage::new(text, MessageType::IMessage);
        normal.effect = effect;
        normal.reply_guid = reply_to_id;
        normal.reply_part = reply_to_part;
        self.dispatch(participants, group_name, sender_guid, Message::Message(normal))
            .await
    }

    /// Sends a message carrying files.
    ///
    /// Attachments do not travel inside the message. Each one is encrypted and
    /// uploaded to Apple's media service first, and what the message carries is
    /// a reference - so this is an upload followed by a send, and a large file
    /// makes it a slow call.
    pub async fn send_attachments(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        text: String,
        files: Vec<OutgoingFile>,
        reply_to_id: Option<String>,
        effect: Option<String>,
    ) -> Result<String, CoreError> {
        let inner = self.inner.lock().await;
        let connection = inner
            .connection
            .clone()
            .ok_or_else(|| CoreError::new("Not connected."))?;
        drop(inner);

        let mut parts: Vec<IndexedMessagePart> = vec![];
        for file in &files {
            let handle = std::fs::File::open(&file.path)
                .map_err(|e| CoreError::new(format!("Couldn't read {}: {e}", file.name)))?;

            // Two passes over the file: one to hash and size it, one to send
            // it. Hence the reopen rather than a rewind - the prepared put
            // consumed the first reader.
            let prepared = MMCSFile::prepare_put(handle)
                .await
                .map_err(|e| CoreError::new(format!("Couldn't prepare {}: {e}", file.name)))?;

            let handle = std::fs::File::open(&file.path)
                .map_err(|e| CoreError::new(format!("Couldn't read {}: {e}", file.name)))?;

            let attachment = Attachment::new_mmcs(
                &connection,
                &prepared,
                handle,
                &file.mime_type,
                &file.uti_type,
                &file.name,
                |_sent, _total| {},
            )
            .await
            .map_err(|e| CoreError::new(format!("Couldn't upload {}: {e}", file.name)))?;

            parts.push(IndexedMessagePart {
                part: MessagePart::Attachment(attachment),
                idx: None,
                ext: None,
            });
        }

        // Text after the files, which is the order Messages shows them in.
        if !text.is_empty() {
            parts.push(IndexedMessagePart {
                part: MessagePart::Text(text, Default::default()),
                idx: None,
                ext: None,
            });
        }

        if parts.is_empty() {
            return Err(CoreError::new("Nothing to send."));
        }

        let mut normal = NormalMessage::new(String::new(), MessageType::IMessage);
        normal.parts = MessageParts(parts);
        normal.effect = effect;
        normal.reply_guid = reply_to_id;

        self.dispatch(participants, group_name, sender_guid, Message::Message(normal))
            .await
    }

    /// Fetches an attachment's bytes to `into_path`.
    ///
    /// Incoming attachments arrive as references, so nothing is on disk until
    /// this runs - which is why a received photo is a placeholder until it is
    /// asked for.
    pub async fn download_attachment(
        &self,
        message_id: String,
        attachment_index: u32,
        into_path: String,
    ) -> Result<(), CoreError> {
        let inner = self.inner.lock().await;
        let connection = inner
            .connection
            .clone()
            .ok_or_else(|| CoreError::new("Not connected."))?;
        drop(inner);

        let attachment = self
            .attachments
            .lock()
            .await
            .get(&(message_id.clone(), attachment_index))
            .cloned()
            .ok_or_else(|| CoreError::new("That attachment is no longer available."))?;

        let file = std::fs::File::create(&into_path)
            .map_err(|e| CoreError::new(format!("Couldn't write the file: {e}")))?;

        attachment
            .get_attachment(&connection, file, |_done, _total| {})
            .await
            .map_err(|e| CoreError::new(format!("Couldn't download: {e}")))?;
        Ok(())
    }

    /// Adds or removes a tapback. `reaction` takes the six built-in names or
    /// any emoji; anything else is rejected rather than silently sent wrong.
    pub async fn send_tapback(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        target_id: String,
        target_part: u64,
        target_text: String,
        reaction: String,
        added: bool,
    ) -> Result<String, CoreError> {
        let reaction = parse_reaction(&reaction)?;
        let react = ReactMessage {
            to_uuid: target_id,
            to_part: Some(target_part),
            reaction: ReactMessageType::React { reaction, enable: added },
            to_text: target_text,
            embedded_profile: None,
        };
        self.dispatch(participants, group_name, sender_guid, Message::React(react))
            .await
    }

    pub async fn send_edit(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        target_id: String,
        target_part: u64,
        new_text: String,
    ) -> Result<String, CoreError> {
        let edit = EditMessage {
            tuuid: target_id,
            edit_part: target_part,
            new_parts: text_parts(new_text),
        };
        self.dispatch(participants, group_name, sender_guid, Message::Edit(edit))
            .await
    }

    pub async fn send_unsend(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        target_id: String,
        target_part: u64,
    ) -> Result<String, CoreError> {
        let unsend = UnsendMessage { tuuid: target_id, edit_part: target_part };
        self.dispatch(participants, group_name, sender_guid, Message::Unsend(unsend))
            .await
    }

    pub async fn send_typing(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        typing: bool,
    ) -> Result<String, CoreError> {
        self.dispatch(
            participants,
            group_name,
            sender_guid,
            Message::Typing(typing, None),
        )
        .await
    }

    /// Tells the other side the conversation was read.
    pub async fn send_read(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
    ) -> Result<String, CoreError> {
        self.dispatch(participants, group_name, sender_guid, Message::Read)
            .await
    }

    pub async fn send_mark_unread(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
    ) -> Result<String, CoreError> {
        self.dispatch(participants, group_name, sender_guid, Message::MarkUnread)
            .await
    }

    /// Which of `handles` are reachable over iMessage rather than SMS - what
    /// decides blue versus green before a message is ever sent.
    pub async fn validate_targets(
        &self,
        handles: Vec<String>,
        sender: String,
    ) -> Result<Vec<String>, CoreError> {
        let inner = self.inner.lock().await;
        let client = inner
            .client
            .as_ref()
            .ok_or_else(|| CoreError::new("not started"))?;
        client
            .identity
            .validate_targets(&handles, "com.apple.madrid", &sender)
            .await
            .map_err(|e| CoreError::new(format!("couldn't look those up: {e}")))
    }

    // --- FaceTime -------------------------------------------------------

    /// Starts a call and rings the people named.
    ///
    /// Returns the call id, which every other call method takes.
    pub async fn place_call(
        &self,
        participants: Vec<String>,
        video: bool,
    ) -> Result<String, CoreError> {
        let (facetime, handle) = self.calling().await?;
        // FaceTime identifies a call by a GUID the caller mints, unlike
        // iMessage where the conversation is the participant list.
        let call_id = uuid::Uuid::new_v4().to_string().to_uppercase();
        facetime
            .create_session(call_id.clone(), handle, &participants, video)
            .await
            .map_err(|e| CoreError::new(format!("Couldn't start the call: {e}")))?;
        Ok(call_id)
    }

    /// Answers a ringing call.
    pub async fn answer_call(&self, call_id: String) -> Result<(), CoreError> {
        let (facetime, _) = self.calling().await?;
        let mut state = facetime.state.write().await;
        let session = state
            .sessions
            .get_mut(&call_id)
            .ok_or_else(|| CoreError::new("That call has already ended."))?;
        // ring = false: answering joins, it doesn't ring anyone else.
        facetime
            .join(session, false)
            .await
            .map_err(|e| CoreError::new(format!("Couldn't join the call: {e}")))
    }

    /// Declines a ringing call, telling the caller rather than just going quiet.
    pub async fn decline_call(&self, call_id: String) -> Result<(), CoreError> {
        self.stop_call_audio(call_id.clone()).await;
        let (facetime, _) = self.calling().await?;
        let mut state = facetime.state.write().await;
        let session = state
            .sessions
            .get_mut(&call_id)
            .ok_or_else(|| CoreError::new("That call has already ended."))?;
        facetime
            .decline_invite(session)
            .await
            .map_err(|e| CoreError::new(format!("Couldn't decline: {e}")))
    }

    /// Hangs up a call already in progress.
    pub async fn end_call(&self, call_id: String) -> Result<(), CoreError> {
        self.stop_call_audio(call_id.clone()).await;
        let (facetime, _) = self.calling().await?;
        let mut state = facetime.state.write().await;
        let session = state
            .sessions
            .get_mut(&call_id)
            .ok_or_else(|| CoreError::new("That call has already ended."))?;
        facetime
            .leave(session)
            .await
            .map_err(|e| CoreError::new(format!("Couldn't hang up: {e}")))
    }

    /// Opens the audio path on a call that has connected.
    ///
    /// Separate from answering on purpose: the media session only exists once
    /// the relay connection is up, which is a moment or two after the call is
    /// answered, and asking for a microphone before then fails.
    pub async fn start_call_audio(&self, call_id: String) -> Result<(), CoreError> {
        let inner = self.inner.lock().await;
        let facetime = inner
            .facetime
            .clone()
            .ok_or_else(|| CoreError::new("FaceTime isn't connected."))?;
        let listener = inner
            .listener
            .clone()
            .ok_or_else(|| CoreError::new("Not started."))?;
        drop(inner);

        let session_conn = {
            let state = facetime.state.read().await;
            state
                .sessions
                .get(&call_id)
                .and_then(|s| s.connection.clone())
                .ok_or_else(|| CoreError::new("That call isn't connected yet."))?
        };

        // Incoming frames arrive on the media thread. The handler only
        // forwards - anything slow here stalls the whole receive path.
        let for_handler = listener.clone();
        session_conn.frame_handler.configure_handler(Box::new(move |message| {
            if message.r#type != ChannelType::Aac {
                // Video frames arrive through the same handler. There's no
                // renderer yet, so they're dropped rather than queued into
                // memory that nothing drains.
                return;
            }
            match message.frame {
                ChannelFrame::Sample(bytes) => {
                    for_handler.on_audio_frame(bytes, message.timestamp)
                }
                ChannelFrame::Configuration(DecoderConfiguration::Raw(config, _)) => {
                    for_handler.on_audio_config(config)
                }
                ChannelFrame::Configuration(_) => {}
            }
        }));

        let sender = session_conn.create_audio_sender(None, &[]).await;
        let mut inner = self.inner.lock().await;
        inner.audio.insert(call_id, Arc::new(Mutex::new(sender)));
        Ok(())
    }

    /// Sends one encoded audio frame from the microphone.
    ///
    /// `timestamp` is the codec's own clock, not wall time - it has to advance
    /// by the frame's sample count or the far end plays the call at the wrong
    /// speed.
    pub async fn send_call_audio(
        &self,
        call_id: String,
        frame: Vec<u8>,
        timestamp: u32,
    ) -> Result<(), CoreError> {
        let sender = {
            let inner = self.inner.lock().await;
            inner
                .audio
                .get(&call_id)
                .cloned()
                .ok_or_else(|| CoreError::new("Audio isn't running for that call."))?
        };
        let mut sender = sender.lock().await;
        sender
            .send_audio_frame(&frame, timestamp)
            .map_err(|e| CoreError::new(format!("Couldn't send audio: {e}")))
    }

    /// Releases the microphone path when a call ends.
    pub async fn stop_call_audio(&self, call_id: String) {
        let mut inner = self.inner.lock().await;
        inner.audio.remove(&call_id);
    }

    /// A shareable FaceTime link, the way iOS's "Create Link" works.
    ///
    /// Useful on its own: a link can be sent to anyone, including people on
    /// devices this app could never call directly.
    pub async fn create_call_link(&self) -> Result<String, CoreError> {
        let (facetime, handle) = self.calling().await?;
        facetime
            .get_link_for_usage(&handle, "cxn")
            .await
            .map_err(|e| CoreError::new(format!("Couldn't create a link: {e}")))
    }

    /// The calls this device currently knows about.
    pub async fn active_calls(&self) -> Result<Vec<CallInfo>, CoreError> {
        let inner = self.inner.lock().await;
        let Some(facetime) = inner.facetime.clone() else { return Ok(vec![]) };
        drop(inner);
        let state = facetime.state.read().await;
        Ok(state
            .sessions
            .iter()
            .filter(|(_, session)| session.start_time.is_some())
            .map(|(id, session)| CallInfo {
                call_id: id.clone(),
                members: session.members.iter().map(|m| m.handle.clone()).collect(),
                is_video: session.is_video,
                outgoing: session.is_initiator,
                started_ms: session.start_time.unwrap_or(session.creation_time),
            })
            .collect())
    }

    /// Clears every trace of the account from disk.
    pub async fn sign_out(&self) -> Result<(), CoreError> {
        let mut inner = self.inner.lock().await;
        *inner = Inner::default();
        for path in [
            self.paths.registration(),
            self.paths.account(),
            self.paths.keystore(),
            self.paths.id_cache(),
        ] {
            let _ = std::fs::remove_file(path);
        }
        let _ = std::fs::remove_dir_all(self.paths.anisette());
        Ok(())
    }
}

impl ImessageCore {
    /// Advances Apple's sign-in state machine until it needs something from
    /// the user, and reports what that is.
    ///
    /// The important part is that several states are *actions*, not screens.
    /// `NeedsDevice2FA` does not mean "a code is on its way" - it means Apple
    /// is willing to send one and is waiting to be asked. Showing a code entry
    /// box on that state, without calling `send_2fa_to_devices`, leaves the
    /// user staring at a field waiting for a message that was never requested.
    async fn drive_login(
        &self,
        inner: &mut Inner,
        mut state: LoginState,
    ) -> Result<LoginStep, CoreError> {
        // Bounded rather than `loop`: each hop is a network round trip, and a
        // state that advances to itself would otherwise hammer Apple forever.
        for _ in 0..6 {
            let account = inner
                .account
                .as_ref()
                .ok_or_else(|| CoreError::new("no sign-in is in progress"))?;

            state = match state {
                LoginState::LoggedIn => return Ok(LoginStep::Complete),

                // Apple has sent something and is waiting for the digits.
                LoginState::Needs2FAVerification => return Ok(LoginStep::NeedsDeviceCode),

                LoginState::NeedsSMS2FAVerification(body) => {
                    inner.sms_verify = Some(body);
                    return Ok(LoginStep::NeedsSmsCode { phone_numbers: vec![] });
                }

                // Ask for the push. This is the call whose absence meant no
                // code ever arrived.
                LoginState::NeedsDevice2FA => account
                    .send_2fa_to_devices()
                    .await
                    .map_err(|e| CoreError::new(format!("Couldn't ask Apple for a code: {e}")))?,

                // Phone id 1 is Apple's first trusted number. An account with
                // no trusted number fails here, and the error says so rather
                // than looking like a rejected password.
                LoginState::NeedsSMS2FA => account
                    .send_sms_2fa_to_devices(1)
                    .await
                    .map_err(|e| CoreError::new(format!("Couldn't send a code by text: {e}")))?,

                // Apple sometimes reports an extra step it has already let us
                // past - if the PET token is in hand, we are actually signed in.
                LoginState::NeedsExtraStep(step) => {
                    return if account.get_pet().is_some() {
                        Ok(LoginStep::Complete)
                    } else {
                        Ok(LoginStep::NeedsWebStep { url: step })
                    };
                }

                LoginState::NeedsLogin => {
                    return Err(CoreError::new(
                        "Apple didn't accept that Apple ID or password.",
                    ));
                }
            };
        }
        Err(CoreError::new("Sign-in didn't settle. Try again."))
    }

    /// The FaceTime client plus the handle to call from.
    async fn calling(&self) -> Result<(Arc<FTClient>, String), CoreError> {
        let inner = self.inner.lock().await;
        let facetime = inner
            .facetime
            .clone()
            .ok_or_else(|| CoreError::new("FaceTime isn't connected yet."))?;
        let client = inner
            .client
            .clone()
            .ok_or_else(|| CoreError::new("Not started."))?;
        drop(inner);
        let handle = client
            .identity
            .get_handles()
            .await
            .first()
            .cloned()
            .ok_or_else(|| CoreError::new("This account has no FaceTime address."))?;
        Ok((facetime, handle))
    }

    /// Pulls the three things every authenticated call needs, with one error
    /// message covering the single cause of all three being absent.
    fn session(
        &self,
        inner: &Inner,
    ) -> Result<
        (Arc<RelayConfig>, APSConnection, ArcAnisetteClient<DefaultAnisetteProvider>),
        CoreError,
    > {
        match (&inner.config, &inner.connection, &inner.anisette) {
            (Some(c), Some(conn), Some(a)) => Ok((c.clone(), conn.clone(), a.clone())),
            _ => Err(CoreError::new("call configure_relay first")),
        }
    }

    /// One stable device UUID per install, generated on first use.
    fn device_uuid(&self) -> String {
        let path = self.paths.root.join("device_uuid");
        if let Ok(existing) = std::fs::read_to_string(&path) {
            let trimmed = existing.trim();
            if !trimmed.is_empty() {
                return trimmed.to_string();
            }
        }
        let fresh = uuid::Uuid::new_v4().to_string();
        let _ = std::fs::write(&path, &fresh);
        fresh
    }

    async fn persist(&self, inner: &mut Inner) -> Result<(), CoreError> {
        let connection = inner
            .connection
            .clone()
            .ok_or_else(|| CoreError::new("not connected"))?;
        let identity = inner
            .identity
            .clone()
            .ok_or_else(|| CoreError::new("no identity"))?;
        let push = connection.state.read().await.clone();
        write_plist(
            &self.paths.registration(),
            &SavedState { push, users: inner.users.clone(), identity },
        )
    }

    /// The shared tail of every send: wrap the payload in a conversation,
    /// stamp it with our handle, hand it to the client.
    async fn dispatch(
        &self,
        participants: Vec<String>,
        group_name: Option<String>,
        sender_guid: Option<String>,
        message: Message,
    ) -> Result<String, CoreError> {
        let inner = self.inner.lock().await;
        let client = inner
            .client
            .clone()
            .ok_or_else(|| CoreError::new("not started"))?;
        drop(inner);

        let handles = client.identity.get_handles().await;
        let handle = handles
            .first()
            .ok_or_else(|| CoreError::new("this account has no iMessage address"))?
            .clone();

        let conversation = ConversationData {
            participants,
            cv_name: group_name,
            // A group without a stable GUID reads as a brand-new thread on the
            // other side every time, so one is minted when the caller has none.
            sender_guid: Some(
                sender_guid.unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
            ),
            after_guid: None,
        };

        let mut instance = MessageInst::new(conversation, &handle, message);
        client
            .send(&mut instance)
            .await
            .map_err(|e| CoreError::new(format!("couldn't send: {e}")))?;
        Ok(instance.id)
    }
}

/// Wraps plain text as a single-part message body.
///
/// `MessageParts` is public but its constructors aren't, so an edit's
/// replacement body has to be built from the tuple struct directly. `idx: None`
/// means "this is part zero", which is right for a one-part edit.
fn text_parts(text: String) -> MessageParts {
    MessageParts(vec![IndexedMessagePart {
        part: MessagePart::Text(text, Default::default()),
        idx: None,
        ext: None,
    }])
}

fn parse_reaction(name: &str) -> Result<Reaction, CoreError> {
    Ok(match name {
        "heart" => Reaction::Heart,
        "like" => Reaction::Like,
        "dislike" => Reaction::Dislike,
        "laugh" => Reaction::Laugh,
        "emphasize" => Reaction::Emphasize,
        "question" => Reaction::Question,
        // Anything else is treated as an emoji tapback, which iOS 18 supports
        // for arbitrary emoji. An empty string isn't one.
        "" => return Err(CoreError::new("no reaction given")),
        emoji => Reaction::Emoji(emoji.to_string()),
    })
}

/// Apple's SRP exchange is defined over SHA-256 of the password, so this is
/// what gets sent - the plaintext password never leaves `login`.
/// Says which of the several very different relay failures actually happened.
///
/// These were previously all flattened into "couldn't reach the relay", which
/// is only true for one of them - a wrong pairing code and a stopped server
/// produced identical text, so the message actively misled instead of helping.
fn relay_error(e: PushError) -> CoreError {
    CoreError::new(match &e {
        // The relay answered. It just didn't like the request - almost always
        // a pairing code that doesn't match the one the server was started with.
        PushError::RelayError(401, _) | PushError::RelayError(403, _) =>
            "The relay is running, but rejected that pairing code. It has to match \
             the RELAY_CODE the server was started with."
                .to_string(),
        PushError::RelayError(status, body) => {
            let detail = body.chars().take(200).collect::<String>();
            format!("The relay answered with HTTP {status}. {detail}")
        }
        // Its own 404 for this endpoint - reachable, but not this API.
        PushError::DeviceNotFound =>
            "Reached the server, but it has no registration relay at that address."
                .to_string(),
        // Anything else really is a connection problem.
        other => format!("Couldn't reach the relay: {other}"),
    })
}

fn hash_password(password: &str) -> Vec<u8> {
    use sha2::{Digest, Sha256};
    Sha256::digest(password.as_bytes()).to_vec()
}

/// Fetches the OS versions a relay reports. Useful on its own as a connectivity
/// check from the setup screen, before any account details are entered.
#[uniffi::export(async_runtime = "tokio")]
pub async fn probe_relay(
    host: String,
    code: String,
    token: Option<String>,
) -> Result<String, CoreError> {
    let versions = RelayConfig::get_versions(&host, &code, &token)
        .await
        .map_err(relay_error)?;
    serde_json::to_string(&versions).map_err(CoreError::new)
}
