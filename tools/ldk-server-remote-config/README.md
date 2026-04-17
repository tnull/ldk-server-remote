# ldk-server-remote-config

Tiny Rust CLI that emits a single-line `ldk-server-remote://setup?...` URI from an
LDK Server data directory. The URI is consumed by the Android app's setup-screen
QR scanner — pipe the output into `qrencode` to produce a scannable image.

## Usage

```bash
cargo build --release
./target/release/ldk-server-remote-config \
    --data-dir ~/.ldk-server \
    --name "home signet" \
    --rpc-address 192.168.1.5:3000 \
    --network signet \
  | qrencode -o ~/setup.png
```

Options:

- `--data-dir <PATH>`: the LDK Server storage directory (the one that holds
  `tls.crt` plus the per-network subdirectory with the `api_key` file). Usually
  `~/.ldk-server` on Linux.
- `--name <STR>`: free-form label the user sees in the phone's server list.
- `--rpc-address <HOST:PORT>`: the address the phone can actually reach the
  server at. **Not inferable** from the data directory — this is typically a LAN
  IP or a public hostname, not `127.0.0.1`.
- `--network <mainnet|testnet|signet|regtest>`: the network the server runs.

## Expected data directory layout

```text
<data-dir>/
├── tls.crt
└── <network>/
    └── api_key
```

Where `<network>` is LDK Server's own directory name: `bitcoin` for mainnet,
`testnet`, `signet`, or `regtest`. The tool translates `--network mainnet` to
the `bitcoin` subdir automatically.

## Format

The emitted URI is versioned (`v=1`) and shaped as:

```
ldk-server-remote://setup?v=1
                          &name=<url-encoded>
                          &network=<mainnet|testnet|signet|regtest>
                          &url=<host:port>
                          &apikey=<hex>
                          &cert=<base64url-encoded PEM, no padding>
```

The Android app's `util/ServerUri.kt` parses the same format.

## Tests

```bash
cd tools/ldk-server-remote-config
cargo test
```
