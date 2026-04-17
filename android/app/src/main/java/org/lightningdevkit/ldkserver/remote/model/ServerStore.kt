package org.lightningdevkit.ldkserver.remote.model

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * CRUD over the list of configured [ServerEntry]s.
 *
 * The whole list is stored as a JSON blob inside a single EncryptedSharedPreferences
 * entry. That's a deliberate choice over one-key-per-server — the list is small, and
 * the single blob keeps the on-disk representation (and migrations) simple.
 *
 * Exposes a [StateFlow] of the current list so the UI reactively refreshes after any
 * mutation.
 */
interface ServerStore {
    val servers: StateFlow<List<ServerEntry>>

    fun get(id: String): ServerEntry?

    fun add(entry: ServerEntry)

    fun update(entry: ServerEntry)

    fun remove(id: String)
}

class EncryptedServerStore(
    prefs: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ServerStore {
    private val prefs = prefs
    private val mutableServers = MutableStateFlow(loadFromDisk())

    override val servers: StateFlow<List<ServerEntry>> = mutableServers.asStateFlow()

    override fun get(id: String): ServerEntry? = mutableServers.value.firstOrNull { it.id == id }

    override fun add(entry: ServerEntry) {
        replaceAll(mutableServers.value + entry)
    }

    override fun update(entry: ServerEntry) {
        val next = mutableServers.value.map { if (it.id == entry.id) entry else it }
        replaceAll(next)
    }

    override fun remove(id: String) {
        replaceAll(mutableServers.value.filterNot { it.id == id })
    }

    private fun replaceAll(next: List<ServerEntry>) {
        mutableServers.value = next
        prefs.edit().putString(SERVERS_KEY, json.encodeToString(next)).apply()
    }

    private fun loadFromDisk(): List<ServerEntry> {
        val raw = prefs.getString(SERVERS_KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerEntry>>(raw) }
            .getOrElse { emptyList() }
    }

    companion object {
        private const val SERVERS_KEY = "servers"
        private const val PREFS_FILE_NAME = "ldk_server_remote_servers"

        /**
         * Factory that hands back an [EncryptedServerStore] backed by
         * [EncryptedSharedPreferences] so both the API keys and the TLS PEM blobs
         * live encrypted at rest.
         */
        fun create(context: Context): EncryptedServerStore {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            return EncryptedServerStore(prefs)
        }
    }
}

/** In-memory [ServerStore] implementation for unit tests and Compose previews. */
class InMemoryServerStore(initial: List<ServerEntry> = emptyList()) : ServerStore {
    private val mutableServers = MutableStateFlow(initial)

    override val servers: StateFlow<List<ServerEntry>> = mutableServers.asStateFlow()

    override fun get(id: String): ServerEntry? = mutableServers.value.firstOrNull { it.id == id }

    override fun add(entry: ServerEntry) {
        mutableServers.value = mutableServers.value + entry
    }

    override fun update(entry: ServerEntry) {
        mutableServers.value = mutableServers.value.map { if (it.id == entry.id) entry else it }
    }

    override fun remove(id: String) {
        mutableServers.value = mutableServers.value.filterNot { it.id == id }
    }
}
