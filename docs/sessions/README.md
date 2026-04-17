# Session archives

Raw JSONL transcripts from Claude Code sessions where substantial work landed
in this repo. Intended as a project memory of why decisions were made —
they're not load-bearing for the build and aren't consulted by CI.

Compressed because they can be large (the initial buildout is 8 MB
uncompressed, ~2 MB gzipped).

Inspect with:

```bash
gunzip -c docs/sessions/2026-04-17-initial-buildout.jsonl.gz | jq . | less
```

The files are JSONL: one JSON object per line, each representing one message
or tool event in the conversation.

## Index

- `2026-04-17-initial-buildout.jsonl.gz` — initial end-to-end buildout. In a
  single ~4h session, this covers:
  - Spec negotiation (iOS → Android pivot, multi-server support, setup-QR flow).
  - UniFFI bindings for `ldk-server-client` on the `uniffi-bindings` branch in
    the sibling ldk-server repo (7 commits).
  - Full Android app: Gradle/Compose scaffold, navigation, setup screen with
    QR scanner, Wallet + Channels + Node tabs, Send + Receive flows,
    herecomesbitcoin.org "peekers", tap-to-copy polish, BIP177/₿-only
    amount formatting, receive-side payment polling.
  - `tools/ldk-server-remote-config/` CLI helper.
  - Emulator smoke-testing on a headless AVD.

  See `docs/ios-handover.md` for the distilled iOS-port brief based on this
  session.
