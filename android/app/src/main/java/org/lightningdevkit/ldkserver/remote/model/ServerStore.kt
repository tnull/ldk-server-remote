package org.lightningdevkit.ldkserver.remote.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * CRUD over the list of configured [ServerEntry]s.
 *
 * The whole list is stored as a single JSON blob inside an [EncryptedBlobStorage].
 * That's a deliberate choice over one-key-per-server — the list is small, and the
 * single blob keeps the on-disk representation (and migrations) simple.
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
    private val storage: EncryptedBlobStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ServerStore {
    // Initial load is blocking so [servers] starts populated rather than flashing an
    // empty list on the first frame. Same semantics as the old EncryptedSharedPreferences
    // path, which also hit disk at construction time.
    private val mutableServers = MutableStateFlow(runBlocking { loadFromDisk() })

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
        // Fire-and-forget disk write. DataStore serializes writes internally, so
        // rapid successive mutations land on disk in submission order.
        scope.launch {
            storage.write(json.encodeToString(next).toByteArray(Charsets.UTF_8))
        }
    }

    private suspend fun loadFromDisk(): List<ServerEntry> {
        val bytes = storage.read() ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<ServerEntry>>(String(bytes, Charsets.UTF_8))
        }.getOrElse { emptyList() }
    }

    companion object {
        /**
         * Factory that hands back an [EncryptedServerStore] backed by a Tink-encrypted
         * DataStore so both the API keys and the TLS PEM blobs live encrypted at rest.
         */
        fun create(context: Context): EncryptedServerStore {
            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    produceFile = {
                        TinkEncryptedBlobStorage.defaultDataStoreFile(context)
                    },
                )
            val storage = TinkEncryptedBlobStorage.create(context, dataStore)
            // Migrate any legacy EncryptedSharedPreferences data before the store reads
            // from the new location — otherwise the first load would see an empty blob
            // and we'd lose the user's server list.
            runBlocking {
                EncryptedServerStoreMigration.migrateIfNeeded(context, storage)
            }
            return EncryptedServerStore(storage)
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
