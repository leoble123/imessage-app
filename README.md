# iMessage (native Android)

A ground-up native Android iMessage client — Kotlin + Jetpack Compose, built
for feel first. No Flutter, no webviews, no Material-by-default look.

## Why a rewrite

The Flutter-based OpenBubbles app works, but its UI is constrained by
BlueBubbles' original structure and Flutter's rendering path. This is a clean
build against the same underlying protocol core (`rustpush`), so the entire
interface can be tuned to iOS motion and layout without fighting inherited
architecture.

## Where it stands

| Area | Status |
|---|---|
| Design system (iOS colors, type scale, spring motion) | Done |
| Chat list — pinned, unread, swipe actions, collapsing large title | Done |
| Conversation — bubble grouping, tails, tapbacks, receipts, typing | Done |
| Interactive edge-swipe back gesture | Done |
| One-tap reveal for unsent messages | Done |
| Mock backend (runs + demoable today) | Done |
| rustpush core wired in (real iMessage) | Scaffolded, not connected |
| Attachments, reactions UI, effects playback | Not yet |

The app **runs today** against `MockBackend`, with realistic sample threads —
so the whole interface is usable and reviewable before the protocol core is
connected.

## Architecture

```
ui/
  theme/      Color.kt · Type/Theme.kt · Motion.kt   ← the "feel" lives here
  components/ BubbleShape · MessageBubble · GlassSurface · SwipeableRow · Avatar
  screens/    ChatListScreen · ConversationScreen
  AppRoot.kt  push/pop stack + interactive back gesture
data/
  Models.kt          Chat · Message · Tapback · GroupPosition
  MessagingBackend.kt   ← the seam
  MockBackend.kt        in-memory, fully working
rust-core/    UniFFI bridge onto rustpush (Kotlin ⇄ Rust)
```

Every screen talks only to `MessagingBackend`. Swapping `MockBackend` for the
rustpush-backed one is a one-line change in `MainActivity` — nothing in the UI
layer needs to know.

## Notes on the iOS feel

A few decisions that matter more than they look:

- **Springs, not curves.** Everything interactive animates on a spring with
  slight overshoot (`Motion.kt`). Material's duration+easing defaults are the
  single biggest tell that an app isn't iOS.
- **Bubble tails are curls, not triangles.** `BubbleShape` draws a continuous
  silhouette that sweeps out of the corner and hooks back — and only the last
  bubble in a same-sender run gets one, with the others tucking their inner
  corners tight so a run reads as one block.
- **Real iOS system colors**, sampled rather than approximated, including the
  vertical gradient on outgoing bubbles (flat `#007AFF` reads noticeably
  flatter than the real thing).
- **The back gesture is interactive**, scrubbing the transition under your
  finger rather than playing a canned animation on release.

## Building

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Requires the Android SDK (compileSdk 35) and JDK 17+. Release builds are
debug-signed so a sideloaded APK installs without keystore setup.

## Connecting the real backend

`rust-core/` holds a UniFFI bridge crate that depends on `rustpush` with
`default-features = false` — deliberately skipping the `macos-validation-data`
feature, because the function it gates
(`HardwareConfig::from_validation_data`) is an unimplemented stub upstream:

```rust
pub fn from_validation_data(data: &[u8]) -> Result<HardwareConfig, AbsintheError> {
    panic!("Not supported with binary!");
}
```

Instead this uses rustpush's `RelayConfig` path, which forwards validation-data
requests over HTTP to a relay you run yourself. That path is fully implemented
upstream and is what actually works.

Remaining to connect it:
1. `cargo ndk -t arm64-v8a build --release` → `.so` into `app/src/main/jniLibs/`
2. Generate Kotlin bindings (`uniffi-bindgen generate --language kotlin`)
3. Implement `RustBackend : MessagingBackend` over those bindings
4. Swap the `backend` line in `MainActivity`
