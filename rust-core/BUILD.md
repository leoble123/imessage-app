# Building the Rust core

This is the real iMessage engine: rustpush, the same protocol implementation
OpenBubbles uses, cross-compiled for the phone and called from Kotlin through
UniFFI instead of from Dart.

## The two things that block a clean `cargo build`

**1. rustpush's submodules use SSH URLs.** `.gitmodules` points at
`git@github.com:...`, and Cargo's submodule handling goes through libgit2,
which does not apply git's `insteadOf` rewrites - so `cargo build` fails on a
machine without SSH keys no matter how git is configured. `setup.sh` clones
rustpush directly, rewrites those URLs to HTTPS, and points Cargo at the local
checkout.

**2. The default feature set cannot work off a Mac.** rustpush's default
feature is `macos-validation-data`, which pulls in `open-absinthe` - a stub
upstream whose implementation is literally `panic!("Not supported with
binary!")`. That is the wall every self-built iMessage client hits: it can
sign in, but it cannot produce the validation data Apple demands.

The way around it is `remote-anisette-v3`, which moves that problem off the
device and asks a relay server for it. That is what your relay exists to
answer, and it is the same route OpenBubbles takes.

## Build

    ./setup.sh                    # clone + patch rustpush, once
    ./build.sh                    # cross-compile and generate Kotlin bindings

`build.sh` drops `libimessage_core.so` into
`app/src/main/jniLibs/arm64-v8a/` and the generated bindings into
`app/build/generated/uniffi/`, both of which the Gradle build already reads.

Requires: Rust with the `aarch64-linux-android` target
(`rustup target add aarch64-linux-android`) and an Android NDK.

## Where it currently stops

Solved so far, in order, each a real blocker:

1. **SSH submodule URLs** - `setup.sh` clones and rewrites them.
2. **`icloud_auth` fails to compile with default features off** - `default_provider`
   and `DefaultAnisetteProvider` live behind `remote-anisette-v3`. Enabled.
3. **`certs/fairplay/` is not in the public repo** - ten FairPlay
   activation keypairs, deliberately withheld. Patched to the
   `certs/legacy-fairplay` pair the project does publish.
4. **`build.rs` ran the UDL path against proc-macro source** - removed.

Remaining, and it needs a toolchain change rather than a patch: rustpush's
own `src/auth.rs` and `src/lib.rs` use `atomic_try_update`, which is an
unstable library feature. **rustpush requires a nightly Rust toolchain.**
Next step is `rustup toolchain install nightly`, add the Android target to
it, and run `build.sh` with `cargo +nightly`. That is a long compile - a
clean run is ~450 crates - so give it a proper window rather than a
session tail.

After the `.so` exists, the Kotlin side is a `RustBackend` implementing the
existing `MessagingBackend` interface. Every screen is already written
against that interface, so nothing in the UI changes when it lands.

## The identity question, which matters more than the build

Even once this compiles, registering a *new* device with Apple needs
validation data, which is what the relay is for. But your phone already has
a registered identity: the OpenBubbles install that works today. Reusing
that identity is a far shorter path than activating a second one, and worth
settling before spending another session on the compile.
