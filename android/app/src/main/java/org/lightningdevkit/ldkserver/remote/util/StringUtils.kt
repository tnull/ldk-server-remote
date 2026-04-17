package org.lightningdevkit.ldkserver.remote.util

/**
 * Shortens a long identifier (node pubkey, txid, hex-encoded hash, …) to a
 * middle-truncated form the user can read at a glance:
 *
 * ```
 * "03abf30d19c8f85c1f2af9b27b0a7a00f0a24b1a9c3d4e5f607182930a1b2c3d4e"
 *   .truncateMiddle(keep = 8)
 *   // → "03abf30d…0a1b2c3d4e"
 * ```
 *
 * Tap-to-copy surfaces in the UI put the full value on the clipboard, so users
 * don't lose the middle bytes. Short strings that already fit pass through unchanged.
 */
fun String.truncateMiddle(keep: Int = 8): String = if (length <= 2 * keep + 1) this else take(keep) + "…" + takeLast(keep)
