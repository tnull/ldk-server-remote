package org.lightningdevkit.ldkserver.remote.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A single configured LDK Server the user can connect to. Persisted (including secrets)
 * via [ServerStore] on top of a Tink-encrypted DataStore.
 *
 * All fields are required. `id` is stable across edits and is how the rest of the app
 * references a server (routes, ViewModels, etc.).
 */
@Serializable
data class ServerEntry(
    val id: String,
    val name: String,
    val network: BitcoinNetwork,
    val baseUrl: String,
    val apiKey: String,
    val certificatePem: String,
    val createdAtEpochSeconds: Long,
) {
    companion object {
        /** Mint a new random id. Used when the user creates a server from the add screen. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}
