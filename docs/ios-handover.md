# iOS port — handover notes

Distilled from the initial Android buildout session (transcript at
`docs/sessions/2026-04-17-initial-buildout.jsonl.gz`). The Android app is the
reference implementation; the iOS port should target feature parity and visual
parity, not an independent redesign.

## Architectural spine

**Do not reimplement gRPC + HMAC auth in Swift.** The entire authenticated
client already exists in Rust (`ldk-server-client`) and is UniFFI-exposed on
the `uniffi-bindings` branch of the sibling `ldk-server` repo. Ship the same
binary as an XCFramework and import the generated Swift module.

- Crate: `ldk-server-client` (path: `../ldk-server/ldk-server-client`) with
  feature flag `uniffi`.
- Bindgen binary lives inside the same crate as a `[[bin]]` target, no separate
  workspace crate. Invoke with:
  ```
  cargo run --features uniffi-cli --bin uniffi-bindgen -p ldk-server-client -- \
    generate --library <path>/libldk_server_client.<dylib|a> --language swift …
  ```
- UniFFI 0.29.5, async_runtime = "tokio". Proc-macro scaffolding, no UDL file.
- Wrapper types are flat analogues of the prost types (see
  `ldk-server-client/src/uniffi_types.rs`). Kotlin and Swift see the same
  records/enums through UniFFI.

**Cross-compile targets for iOS:**
```
aarch64-apple-ios
aarch64-apple-ios-sim
x86_64-apple-ios          # optional, for Intel Macs
```
The Kotlin generator produces `.kt` + `.so`; Swift generator produces `.swift`
+ a `.a`/`.dylib` + a modulemap. Package as an XCFramework (lipo together
the simulator slices; aarch64-ios stays separate). See `ldk-node`'s
`scripts/swift_create_xcframework_archive.sh` for the exact shape; our
`scripts/uniffi_bindgen_generate_kotlin_android.sh` is the sibling script we
already wrote for Android.

## Repo layout (reserved)

```
ldk-server-remote/
├── android/   ← existing Kotlin+Compose app, feature-complete
├── ios/       ← create this
└── tools/ldk-server-remote-config/   ← CLI emits setup URIs (shared)
```

The CLI is platform-agnostic and its `ldk-server-remote://setup?v=1&…` URI
format is the contract between the CLI and the app's QR scanner. See
`tools/ldk-server-remote-config/src/main.rs` (Rust) and
`android/app/src/main/java/org/lightningdevkit/ldkserver/remote/util/ServerUri.kt`
(Kotlin parser) for the authoritative format definition.

## Tab + flow surface

Three tabs per server. Each tab is a single screen with modal overlays for
sub-flows.

### Landing + setup (outside the tabs)

1. **Server list** — primary screen. Rows: name + network chip + URL.
   Long-press → edit / remove. Tap row → enter main tabs for that server.
   Empty state: "Add your first server" extended FAB.
2. **Add/Edit server** — modal form: Name, Network dropdown
   (Mainnet / Testnet / Signet / Regtest), host:port URL, API key, TLS PEM.
   **Prominent "Scan setup QR" button** at the top that pre-fills every field
   from the `ldk-server-remote:` URI. Fields stay editable after scan — critical.
   Saving does **not** attempt a connection; connection is lazy on row tap.

### Main tabs

1. **Wallet.** BalanceCard (total in ₿-only format + on-chain/Lightning breakdown)
   → two big Send/Receive buttons → Activity list (recent payments with direction
   badge, kind label, status dot, relative timestamp, signed amount).
   Pull-to-refresh.
2. **Channels.** Rows with counterparty (truncated), status dot, capacity bar.
   FAB → Open channel form. Tap row → detail with close / force-close.
3. **Node.** NodeInfoCard (node id, URIs, block height) + SyncStatusCard (5
   staleness-colored rows) + PeerList. **Every identifier is tap-to-copy**
   with the full value going to the clipboard. FAB → Connect peer.

### Flows (full-screen modals over the tab that launched them)

- **Send.** Input → Confirm → Result state machine. Unified paste-or-scan
  input (accepts BOLT11, BOLT12, BIP21, on-chain, BIP353). Opportunistic
  `decode_invoice` / `decode_offer` on Next. Confirm shows decoded details.
  `unified_send` fires the payment.
- **Receive.** TypePicker → Form → QR → Received. On-chain skips the form.
  Generated BOLT11/BOLT12 payloads are polled against `list_payments` every
  3s; a matching inbound SUCCEEDED payment flips the flow to a celebration
  screen with the amount, before auto-dismissing into a refreshed Wallet tab.

## UX invariants

These came from the Bitcoin Design Guide and were negotiated during the
buildout. **Preserve them on iOS.**

- **No wallet balance on the Receive screen** — privacy against nearby
  onlookers in physical-world transactions.
- **₿-only amount format for rendered values** (e.g. `₿5,449`, no space, no
  `sats` word). But **keep `sats` in form-input labels** — `(₿)` in a text
  field reads ambiguously against `(BTC)` and users could type `1` thinking
  they're sending one whole coin.
- **Uppercase bech32 QR payloads.** Alphanumeric QR mode = 3.5 bits/char vs
  byte mode's 8 bits/char → ~2x denser, easier to scan.
- **Auto-boost screen brightness while the QR is visible**, restore on
  dispose. See `android/.../ui/receive/BrightnessController.kt`.
- **Portrait-only QR scanner, with an explicit X close button.** Our
  `PortraitCaptureActivity` subclasses zxing's CaptureActivity and overrides
  the layout.
- **Network chip color reflects network, not connection state.** Green,
  purple, orange, grey mean signet, testnet, mainnet, regtest — never "peer
  offline".
- **Multi-server from day one.** No global "selected server" state; it lives
  in the nav graph's path args so back-stack semantics stay correct.
- **Whimsical peekers** from `herecomesbitcoin.org` on mostly-empty screens
  (server list, Wallet with sparse activity, Node with empty peers, Receive
  type picker, Send success). Currently in `android/.../res/drawable-nodpi/peeker_*.png`,
  17 characters, randomly chosen + mirrored-by-corner on each composition via
  `remember`. Swift side should load the same PNG set from an asset
  catalog.

## UniFFI surface

`LdkServerClientUni` (returned from `LdkServerClientUni(baseUrl, apiKey, serverCertPem)`)
exposes 18 async methods:

```
get_node_info, get_balances, list_channels, list_peers, list_payments,
get_payment_details, onchain_receive, bolt11_receive, bolt12_receive,
unified_send, bolt11_send, bolt12_send, onchain_send, open_channel,
close_channel, force_close_channel, connect_peer, disconnect_peer,
decode_invoice, decode_offer
```

Errors are a single `LdkServerClientError` enum with `InvalidRequest`,
`AuthenticationFailed`, `LightningError`, `InternalServerError`,
`InternalError` variants — each has a `reason: String` payload (NOT `message`,
which collides with Throwable on JVM; Swift doesn't have the collision but
keeping the name consistent across bindings pays off).

Generated Kotlin types lived under `org.lightningdevkit.ldkserver.client.*`
(see `android/app/src/generated/kotlin/…/ldk_server_client.kt`). Swift output
should live under a Swift module named the same as `uniffi.toml` specifies
(currently Android-only; add a `uniffi-ios.toml` for the iOS build).

## Gotchas the Android side hit

1. **`message` field on UniFFI error variants.** On Kotlin it collides with
   `Throwable.message`. Renamed to `reason`. Swift wouldn't hit the issue but
   don't rename the field.
2. **UniFFI generator emits snake_case filenames, wildcard imports, etc.**
   For Android we excluded the generated file from ktlint via `.editorconfig`
   glob. SwiftLint will want the same treatment (exclude `*.swift` under the
   bindings path from rules).
3. **JNA was needed at runtime for UniFFI Kotlin.** Swift has no equivalent
   dependency — the generated Swift directly references the C ABI of the
   cdylib/staticlib.
4. **`while (isActive) delay(N)` + `advanceUntilIdle()` in unit tests loops
   forever in virtual time.** The receive-polling tests use `advanceTimeBy(N)`
   to bound. Swift tests will need the same bound pattern if you use a similar
   scheduler.
5. **QR code density.** ~1.4 KB PEM base64-encoded → ~2 KB URI → QR version
   27–30. Scanable at arm's length but requires a decent camera. Don't try to
   shrink by removing fields — every field (name, network, URL, apikey, cert)
   is needed.
6. **Emulator with software-only GPU boots in 10+ minutes; with KVM in ~50s.**
   iOS Simulator is uniformly fast; less of a concern.

## Project memory entries

See `~/.claude/projects/-home-tnull-workspace-ldk-server-remote/memory/` for
auto-memory entries a future Claude session will see automatically. The
project overview there is a shorter version of this document.

## Commits produced this session

Rust (`~/workspace/ldk-server` branch `uniffi-bindings`, 7 commits):
```
e0df7af  uniffi feature gate and single-crate bindgen bin
f71108c  wrapper types and conversions
97f0bb7  LdkServerClientUni with query methods
9fbd9ea  send/receive/channel/peer methods
7cdb2e7  conversion tests
f60962b  android cross-compile + kotlin bindings script
1521bea  rename uniffi error field message -> reason
```

Android (`~/workspace/ldk-server-remote` master, 19 commits):
```
ff61abc  initial Gradle + Compose skeleton
a1dc2ac  wire up uniffi bindings + native libs
0e4dfe1  LdkService + FakeLdkService + multi-server store
f6a19bb  top-level navigation: server list -> tabs
7aaa7c6  add/edit server with network + QR scan prefill
d79b2f1  ldk-server-remote-config CLI
98c5c29  portrait QR scanner + close button
d49bb03  Wallet tab
1b23e47  Send flow
6505989  Receive flow
a793a1a  Channels tab
fde80e5  Node tab
8056103  peekers on empty screens
79d1614  tap-to-copy polish on Node tab + truncateMiddle util
5012a03  ₿-only amount format
b4b53fb  peekers on more places and behind populated server list
3489720  receive polling + success sheet
```
