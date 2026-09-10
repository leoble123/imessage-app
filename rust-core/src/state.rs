//! What has to survive an app restart, and where it lives.
//!
//! iMessage registration is not free: each `register` call consumes a device
//! slot on the Apple ID and, done repeatedly, gets the account flagged. So the
//! push connection state, the IDS users and the NGM identity are written to
//! disk the moment they change and reloaded on launch, exactly the way the
//! upstream reference client does it. Losing this file means re-registering.

use std::path::{Path, PathBuf};

use rustpush::{APSState, IDSNGMIdentity, IDSUser};
use serde::{Deserialize, Serialize};

use crate::types::CoreError;

#[derive(Serialize, Deserialize, Clone)]
pub struct SavedState {
    pub push: APSState,
    pub users: Vec<IDSUser>,
    pub identity: IDSNGMIdentity,
}

/// Every path the core writes to, all under one directory the app owns.
#[derive(Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Paths { root: root.into() }
    }

    /// Registration state: push connection, IDS users, NGM identity.
    pub fn registration(&self) -> PathBuf {
        self.root.join("registration.plist")
    }

    /// The Apple ID session - tokens and the hashed password, so the account
    /// can re-authenticate without prompting again.
    pub fn account(&self) -> PathBuf {
        self.root.join("account.plist")
    }

    /// RSA/EC key material, held through rustpush's keystore abstraction.
    pub fn keystore(&self) -> PathBuf {
        self.root.join("keystore.plist")
    }

    /// IDS lookup cache. Purely an optimisation - deleting it costs a round
    /// trip per conversation, nothing more.
    pub fn id_cache(&self) -> PathBuf {
        self.root.join("id_cache.plist")
    }

    /// Working directory for the anisette provider.
    pub fn anisette(&self) -> PathBuf {
        self.root.join("anisette")
    }

    pub fn ensure(&self) -> Result<(), CoreError> {
        std::fs::create_dir_all(&self.root).map_err(CoreError::new)?;
        std::fs::create_dir_all(self.anisette()).map_err(CoreError::new)?;
        Ok(())
    }
}

/// Reads a plist, treating "missing" and "corrupt" alike as "not set up yet".
///
/// A half-written state file would otherwise brick the app on every launch with
/// no way out but clearing app data; falling back to a fresh login is
/// recoverable, and the only cost is one re-registration.
pub fn read_plist<T: serde::de::DeserializeOwned>(path: &Path) -> Option<T> {
    plist::from_file(path).ok()
}

pub fn write_plist<T: Serialize>(path: &Path, value: &T) -> Result<(), CoreError> {
    plist::to_file_xml(path, value).map_err(CoreError::new)
}
