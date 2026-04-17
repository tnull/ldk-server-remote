package org.lightningdevkit.ldkserver.remote.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises CRUD + the reactive `servers` flow. Uses an [InMemoryEncryptedBlobStorage]
 * fake so these tests stay JVM-only — the real Tink encryption path is exercised in
 * [TinkEncryptedBlobStorageTest].
 *
 * The store is built with a [Dispatchers.Unconfined]-backed scope so fire-and-forget
 * writes settle synchronously within the test body, without virtual-time fiddling.
 */
class ServerStoreTest {
    private fun newStore(storage: EncryptedBlobStorage = InMemoryEncryptedBlobStorage()): EncryptedServerStore =
        EncryptedServerStore(
            storage = storage,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun starts_empty() =
        runTest {
            val store = newStore()
            assertTrue(store.servers.first().isEmpty())
        }

    @Test
    fun add_get_update_remove_roundtrip() =
        runTest {
            val store = newStore()
            val entry =
                ServerEntry(
                    id = "id-1",
                    name = "signet demo",
                    network = BitcoinNetwork.SIGNET,
                    baseUrl = "example.com:3000",
                    apiKey = "cafef00d",
                    certificatePem = "-----BEGIN CERTIFICATE-----\nabcd\n-----END CERTIFICATE-----\n",
                    createdAtEpochSeconds = 1_700_000_000L,
                )
            store.add(entry)
            assertEquals(listOf(entry), store.servers.first())
            assertEquals(entry, store.get("id-1"))

            val renamed = entry.copy(name = "signet (renamed)")
            store.update(renamed)
            assertEquals(renamed, store.get("id-1"))

            store.remove("id-1")
            assertNull(store.get("id-1"))
            assertTrue(store.servers.first().isEmpty())
        }

    @Test
    fun update_nonexistent_id_is_noop() =
        runTest {
            val store = newStore()
            val ghost =
                ServerEntry(
                    id = "nobody",
                    name = "x",
                    network = BitcoinNetwork.REGTEST,
                    baseUrl = "x",
                    apiKey = "x",
                    certificatePem = "x",
                    createdAtEpochSeconds = 0L,
                )
            store.update(ghost)
            assertTrue(store.servers.first().isEmpty())
        }

    @Test
    fun persists_across_store_instances() =
        runTest {
            val shared = InMemoryEncryptedBlobStorage()

            val first = newStore(shared)
            first.add(
                ServerEntry(
                    id = "a",
                    name = "a",
                    network = BitcoinNetwork.MAINNET,
                    baseUrl = "a",
                    apiKey = "k",
                    certificatePem = "c",
                    createdAtEpochSeconds = 1L,
                ),
            )

            val second = newStore(shared)
            assertNotNull(second.get("a"))
            assertEquals(BitcoinNetwork.MAINNET, second.get("a")!!.network)
        }

    @Test
    fun corrupt_stored_bytes_are_handled_gracefully() =
        runTest {
            val shared = InMemoryEncryptedBlobStorage()
            runBlocking { shared.write("{not valid json".toByteArray()) }

            val store = newStore(shared)
            // Rather than crashing at startup we should treat corrupt state as "no
            // servers" — the user can always add one again. Losing a server entry is
            // much less bad than an unlaunchable app.
            assertTrue(store.servers.first().isEmpty())
        }
}

/** Trivial in-memory stand-in for [EncryptedBlobStorage]. No encryption, no persistence. */
private class InMemoryEncryptedBlobStorage : EncryptedBlobStorage {
    @Volatile private var blob: ByteArray? = null

    override suspend fun read(): ByteArray? = blob?.copyOf()

    override suspend fun write(bytes: ByteArray) {
        blob = bytes.copyOf()
    }
}
