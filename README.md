# LDK Server Remote

A mobile wallet-style remote control for one or more [LDK Server](https://github.com/lightningdevkit/ldk-server) instances.

The app lets you manage multiple configured servers side-by-side (for example a signet and a mainnet node), view balances and payment history, send and receive on-chain / BOLT11 / BOLT12 payments via paste or QR, and open/close Lightning channels.

## Repository layout

```
ldk-server-remote/
├── android/   ← Kotlin + Jetpack Compose app (this is what's built today)
├── ios/       ← reserved for a future SwiftUI port (not created yet)
└── tools/
    └── ldk-server-remote-config/  ← Rust CLI that emits a QR-ready setup URI
```

## Quickest way to add a server

On the machine running LDK Server:

```bash
cd tools/ldk-server-remote-config
cargo run --release -- \
    --data-dir ~/.ldk-server \
    --name "home signet" \
    --rpc-address 192.168.1.5:3000 \
    --network signet \
  | qrencode -o setup.png
```

In the Android app, tap "+" → "Scan setup QR" and aim at the image. All five
fields (name, network, URL, API key, TLS cert) are pre-filled; tweak as
needed (e.g. to adjust the IP for the network you're on) and hit Save.

The Rust side — UniFFI bindings for `ldk-server-client` — lives in the [ldk-server](https://github.com/lightningdevkit/ldk-server) repo on a dedicated branch. The Android app consumes those bindings as a local source set plus native libraries.

## Architectural choices

- **UniFFI, not raw gRPC from Kotlin.** Authentication (HMAC-SHA256), gRPC framing, and the entire type system are already implemented in the Rust client; we expose them via UniFFI rather than re-implementing them.
- **Multi-server from day one.** The landing screen is a list of configured servers (with a network chip), and tapping one transitions into that server's tabbed main UI. Adding a server doesn't attempt a connection — connection is lazy.
- **No secrets in the repo.** API keys, TLS certs, and server URLs come from the in-app Setup screen (persisted locally in a Tink-encrypted Jetpack DataStore, wrapped by a hardware-backed Android Keystore key) or, for automated tests, from environment variables.

## Building the APK

Prerequisites (one-time):

1. Android SDK + NDK under `~/Android/Sdk` (`ANDROID_HOME`, `ANDROID_NDK_ROOT` exported)
2. Rust 1.93+ with Android targets: `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`
3. `cargo install cargo-ndk`
4. JDK 17+ on `$PATH` (JDK 21 recommended)

```bash
# 1. Generate UniFFI Kotlin bindings + cross-compiled .so files.
# This must run from the ldk-server repo (which provides the Rust source).
cd ../ldk-server
OUT_DIR=../ldk-server-remote/android/app/src/main \
    ./scripts/uniffi_bindgen_generate_kotlin_android.sh

# 2. Build the debug APK.
cd ../ldk-server-remote/android
./gradlew assembleDebug

# APK lands at: android/app/build/outputs/apk/debug/app-debug.apk
```

Install on a device:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Testing

JVM unit tests only — no device required:

```bash
cd android
./gradlew test ktlintCheck
```

Optional end-to-end smoke tests that hit a real LDK Server are gated on environment variables; they're skipped unless all three are set:

```bash
LDK_SERVER_URL=example.com:3000 \
LDK_SERVER_API_KEY=<hex> \
LDK_SERVER_TLS_CERT_PATH=/path/to/tls.crt \
./gradlew connectedAndroidTest
```
