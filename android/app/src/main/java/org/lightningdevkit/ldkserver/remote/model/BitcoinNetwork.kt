package org.lightningdevkit.ldkserver.remote.model

import kotlinx.serialization.Serializable

/**
 * The Bitcoin network a configured LDK Server instance is operating on. Purely a
 * user-provided label — we do NOT attempt to auto-detect from the server. Used for:
 *
 *  - a colored chip next to the server's name in the server list, so the user can tell
 *    a signet node apart from a mainnet one at a glance; and
 *
 *  - a warning when they're about to do something risky on a mainnet server.
 */
@Serializable
enum class BitcoinNetwork {
    MAINNET,
    TESTNET,
    SIGNET,
    REGTEST,
    ;

    /** Short, lowercase human-readable name (e.g. "signet"), matching the BIP173 hrp.  */
    fun displayName(): String =
        when (this) {
            MAINNET -> "mainnet"
            TESTNET -> "testnet"
            SIGNET -> "signet"
            REGTEST -> "regtest"
        }
}
