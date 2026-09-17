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

## QR setup

The setup screen leads with a **Scan QR Code** action, so pairing doesn't mean
hand-typing a host and a pairing code, or a server URL and its password.
Camera permission is requested only once scanning is actually started - the
rest of setup never touches it. The scanner (`QrScannerScreen.kt`) is CameraX
for the preview and frame delivery, ML Kit for the decode, restricted to the
QR format only; it stops itself after the first successful decode rather than
continuing to scan.

**Paste a code instead** sits beside it and takes the same formats as text,
for when a code won't scan - a dense export on a dim screen is the usual
reason.

What a code can be (`QrSetupCode.kt`):

- **A BlueBubbles server's own QR** - `["password","https://host"]`, the same
  two-element JSON array the upstream OpenBubbles app's server-connection QR
  already encodes. A BlueBubbles/OpenBubbles server's existing QR code works
  here unchanged; nothing new has to be generated for it.
- **A relay** - `{"host":"...","code":"...","token":"..."}` (`token` optional,
  and `relayHost`/`relay_host`, `pairingCode`/`pairing_code` accepted as
  aliases), or the **plain fallback** `host|code` / `host|code|token` for an
  operator with no JSON tooling on hand - e.g.
  `qrencode "150.136.167.146:5005|abc123"`. The token is what a *public* relay
  (Beeper's) authenticates with on top of the pairing code; a self-hosted
  relay takes the code alone, which is why the field is marked optional.
- **An OpenAbsinthe hardware export** - the `OABS` blob OpenBubbles' Mac
  exporter produces, base64 when pasted and raw bytes when scanned.

That last one is **recognised but refused**, and deliberately so. A hardware
config has to generate its own validation data for every registration, and
that work lives in `open-absinthe`'s `ValidationCtx`, whose `new`,
`key_establishment` and `sign` are all `todo!()` in the public tree (with
`HardwareConfig::from_validation_data` panicking outright). Nobody computes
NAC on the phone from public code - the shipping clients that do it carry
Apple's own, which is not something this repo will reproduce. So the app reads
the export, says which Mac it describes (`describe_hardware` in the core
parses it without touching the network), and explains that the same Mac can
still get you in by running either BlueBubbles Server or a registration relay.
Reading it and saying why beats a scan that silently does nothing, which is
what reading only ML Kit's text value produced - an export's QR is binary, so
`rawValue` is null for it and only `rawBytes` has anything in it.

Everything that isn't refused feeds straight into the same
`AccountManager.connectToMacServer` / `configureRelay` calls the manual fields
use - the QR and paste paths are faster ways to fill in the same setup, not a
second one. A code the parser doesn't recognise shows an inline error and
leaves manual entry available rather than getting stuck. Nothing decoded is
ever logged: it's a server password, a pairing code or a machine's identity,
whichever shape it arrived in.
