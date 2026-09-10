# Building the Rust core

`./rust-core/build.sh` does everything: cross-compiles for the phone,
regenerates the Kotlin bindings from the compiled library, strips it, and
installs it into `app/src/main/jniLibs/arm64-v8a/`.

Run it after any change to `rust-core/src`. The Kotlin bindings are generated
from the compiled `.so`, not from the source, so skipping the regenerate step
leaves the app calling an API the library no longer has — which surfaces as a
crash at the first call rather than a compile error.

## Requirements

- **Nightly Rust.** Not optional: rustpush uses the unstable
  `atomic_try_update` feature in `src/auth.rs` and `src/lib.rs`, so stable
  fails with E0658 before it compiles a line of our code.
  `rustup toolchain install nightly && rustup +nightly target add aarch64-linux-android`
- **Android NDK r27.** Set `ANDROID_NDK_HOME` if it isn't at the default path
  in `build.sh`.
- `./rust-core/setup.sh` once, to vendor rustpush into `vendor/`.

## What was in the way

Six separate walls stood between "clone rustpush" and "a library that links
for Android". They're all handled now, but each is easy to reintroduce:

**1. Submodules declared with SSH URLs.** rustpush's `.gitmodules` uses
`git@github.com:` throughout. Cargo resolves submodules through libgit2, which
does *not* honour git's `insteadOf` rewrites — so configuring git to rewrite
SSH to HTTPS looks like it should work and doesn't. `setup.sh` rewrites the
URLs in the vendored copy instead.

**2. `icloud_auth` wouldn't resolve `default_provider`.** It's behind the
`remote-anisette-v3` feature, which the default build doesn't enable. Turned on
explicitly in `Cargo.toml`.

**3. Missing FairPlay certificates.** `src/activation.rs` points at ten modern
FairPlay keypairs in `certs/fairplay/` that are deliberately withheld from the
public repository. The vendored copy is patched to use the published legacy
pair in `certs/legacy-fairplay/`.

**4. UDL versus proc-macro mode.** The original `build.rs` called UniFFI's
`generate_scaffolding()`, which is the `.udl`-file path, against source that
uses `setup_scaffolding!()` — the proc-macro path. It tried to parse Rust as
IDL and died on line one. `build.rs` is deleted; the macro is the whole of it.

**5. Two incompatible `quinn` crates.** rustpush vendors a patched quinn in
`third_party/quinn` and redirects crates.io to it with `[patch.crates-io]`.
Cargo only honours `[patch]` from the *workspace root* manifest — which is this
crate, not rustpush's — so without repeating the patch here, `h3-quinn` linked
against crates.io quinn while rustpush handed it a `Connection` from the fork,
and `ids/link.rs` failed with a type mismatch between two identically-named
types. The patch is now in `rust-core/Cargo.toml`.

Note that adding a `[patch]` section does *not* on its own make Cargo
re-resolve: a lockfile that's still satisfiable is left alone, and the patch is
silently ignored with no warning. `cargo update` forces the re-resolve.

**6. An error field named `message`.** UniFFI turns error variants into Kotlin
exception subclasses, so a field called `message` collides with
`Throwable.message` and fails to compile on the Kotlin side with an overload
ambiguity pointing at generated code. `CoreError` uses `reason`.

A seventh thing worth knowing: UniFFI rejects `Result<T, String>` outright,
panicking with `unknown throw type: Some(String)` during generation. Errors
have to be a real `uniffi::Error` enum.

## Why the bindgen lives in its own crate

`bindgen/` is a standalone binary that depends only on `uniffi`. UniFFI's
generator normally sits as a `[[bin]]` inside the crate being bound, but that
would mean compiling all of rustpush for the host just to emit Kotlin — a
second full dependency tree, for nothing. In `--library` mode the generator
reads its metadata straight out of the compiled `.so`, so it doesn't need to
know about our crate at all.

The `uniffi` version there must match the one the `.so` was built with
exactly; the metadata format is versioned.

## Why a relay is required at all

Registering with Apple's IDS needs validation data — a blob signed by Apple
hardware. rustpush can generate it on macOS via the `macos-validation-data`
feature, but that path is an unimplemented stub in the public tree
(`HardwareConfig::from_validation_data` is `panic!("Not supported with
binary!")`) and couldn't work on a phone regardless. The `RelayConfig` path
asks a server for it instead, which is what OpenBubbles itself does. That's why
`configure_relay` has to be called before anything else, and why
`default-features = false` is set on the rustpush dependency.
