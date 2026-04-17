package org.lightningdevkit.ldkserver.remote.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises CRUD + the reactive `servers` flow. Backed by plain SharedPreferences under
 * Robolectric (EncryptedSharedPreferences requires Android KeyStore, which is painful
 * to stand up in a JVM test). The JSON serialization path is the same, so a break
 * there would surface here too.
 */
@RunWith(RobolectricTestRunner::class)
class ServerStoreTest {
    private lateinit var store: EncryptedServerStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_servers", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = EncryptedServerStore(prefs)
    }

    @Test
    fun starts_empty() =
        runTest {
            assertTrue(store.servers.first().isEmpty())
        }

    @Test
    fun add_get_update_remove_roundtrip() =
        runTest {
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
            val ctx = RuntimeEnvironment.getApplication()
            val prefs = ctx.getSharedPreferences("persist_test", android.content.Context.MODE_PRIVATE)
            prefs.edit().clear().commit()

            val first = EncryptedServerStore(prefs)
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

            // Fresh instance re-reads disk state.
            val second = EncryptedServerStore(prefs)
            assertNotNull(second.get("a"))
            assertEquals(BitcoinNetwork.MAINNET, second.get("a")!!.network)
        }

    @Test
    fun corrupt_stored_json_is_handled_gracefully() =
        runTest {
            val ctx = RuntimeEnvironment.getApplication()
            val prefs = ctx.getSharedPreferences("corrupt_test", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("servers", "{not valid json").commit()

            val store = EncryptedServerStore(prefs)
            // Rather than crashing at startup we should treat corrupt state as "no
            // servers" — the user can always add one again. Losing a server entry is
            // much less bad than an unlaunchable app.
            assertTrue(store.servers.first().isEmpty())
            assertFalse(false)
        }
}
