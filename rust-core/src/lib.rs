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
    MessagePart, IndexedMessagePart, MADRID_SERVICE,
};
use tokio::sync::Mutex;

use state::{read_plist, write_plist, Paths, SavedState};
pub use types::*;

/// The IDS services to register for.
///
/// Only Madrid (iMessage proper) for now. FaceTime needs `FACETIME_SERVICE` and
/// `VIDEO_SERVICE` added here, but registering for a service the app can't
/// answer makes the account advertise a capability it doesn't have, so they
/// stay out until there's a call UI to back them.
///
/// This is a function rather than a `static` because rustpush doesn't export
/// the `IDSService` type - only the constants - so the type can't be written
/// down. Returning a reference to a const expression promotes it to 'static,
/// which is the lifetime `register` and `IMClient::new` require.
macro_rules! services {
    () => {
        &[&MADRID_SERVICE][..]
    };
}

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
    /// Set once the receive loop is running, so starting twice is a no-op
    /// rather than two loops racing to consume the same broadcast channel.
    receiving: bool,
}

/// The handle Kotlin holds. One per app process.
#[derive(uniffi::Object)]
pub struct ImessageCore {
    paths: Paths,
    inner: Mutex<Inner>,
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
        })
    }

    /// True when a previous session registered successfully and its state
    /// survived - the app can go straight to the conversation list.
    pub fn is_registered(&self) -> bool {
        read_plist::<SavedState>(&self.paths.registration())
            .map(|s| !s.users.is_empty() && s.users.iter().any(|u| !u.registration.is_empty()))
            .unwrap_or(false)
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

        let step = classify(&result);
        inner.account = Some(account);
        Ok(step)
    }

    /// Submits a six-digit code from a trusted device.
    pub async fn submit_device_code(&self, code: String) -> Result<LoginStep, CoreError> {
        let mut inner = self.inner.lock().await;
        let account = inner
            .account
            .as_mut()
            .ok_or_else(|| CoreError::new("no sign-in is in progress"))?;

        let result = account
            .verify_2fa(code)
            .await
            .map_err(|e| CoreError::new(format!("that code didn't work: {e}")))?;
        Ok(classify(&result))
    }

    /// Asks Apple to text a code to one of the account's trusted numbers.
    pub async fn request_sms_code(&self, phone_id: u32) -> Result<LoginStep, CoreError> {
        let inner = self.inner.lock().await;
        let account = inner
            .account
            .as_ref()
            .ok_or_else(|| CoreError::new("no sign-in is in progress"))?;

        let result = account
            .send_sms_2fa_to_devices(phone_id)
            .await
            .map_err(|e| CoreError::new(format!("couldn't send the code: {e}")))?;
        Ok(classify(&result))
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

        if inner.users.iter().any(|u| u.registration.is_empty()) {
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

        inner.client = Some(client.clone());
        inner.receiving = true;
        drop(inner);

        // The receive loop owns its own task for the life of the process. It
        // reads from the push connection's broadcast channel, so it must exist
        // before any message arrives or that message is simply dropped.
        let mut subscription = connection.messages_cont.subscribe();
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

                match client.handle(raw).await {
                    Ok(Some(message)) => {
                        // `has_payload` is false for the protocol's own
                        // bookkeeping traffic, which has nothing to show.
                        if !message.has_payload() {
                            continue;
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

/// Maps Apple's login state onto the screen the app should show next.
fn classify(state: &LoginState) -> LoginStep {
    match state {
        LoginState::LoggedIn => LoginStep::Complete,
        LoginState::NeedsDevice2FA | LoginState::Needs2FAVerification => {
            LoginStep::NeedsDeviceCode
        }
        LoginState::NeedsSMS2FA | LoginState::NeedsSMS2FAVerification(_) => {
            LoginStep::NeedsSmsCode { phone_numbers: vec![] }
        }
        LoginState::NeedsExtraStep(url) => LoginStep::NeedsWebStep { url: url.clone() },
        LoginState::NeedsLogin => LoginStep::NeedsWebStep {
            url: String::new(),
        },
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
