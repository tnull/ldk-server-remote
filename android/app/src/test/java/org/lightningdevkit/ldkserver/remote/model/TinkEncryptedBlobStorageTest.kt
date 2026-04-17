package org.lightningdevkit.ldkserver.remote.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the Tink + DataStore storage layer in isolation. Uses a freshly-generated
 * in-process Tink keyset instead of the Android Keystore — that keeps the test JVM-only
 * (no Robolectric, no Keystore shims), while still exercising the real encrypt/decrypt
 * code path.
 */
class TinkEncryptedBlobStorageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var aead: Aead

    @Before
    fun setUp() {
        AeadConfig.register()
        aead =
            KeysetHandle
                .generateNew(KeyTemplates.get("AES256_GCM"))
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun newDataStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // DataStore requires its own scope for the internal actor; tie it to the
            // test lifetime via a SupervisorJob + IO dispatcher (matches production).
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )

    @Test
    fun read_returns_null_before_any_write() =
        runBlocking {
            val store =
                TinkEncryptedBlobStorage(
                    newDataStore(tempFolder.newFile("ds1.preferences_pb")),
                    aead,
                )
            assertNull(store.read())
        }

    @Test
    fun write_then_read_returns_same_plaintext() =
        runBlocking {
            val store =
                TinkEncryptedBlobStorage(
                    newDataStore(tempFolder.newFile("ds2.preferences_pb")),
                    aead,
                )
            val payload = "hello, mainnet".toByteArray()
            store.write(payload)
            assertArrayEquals(payload, store.read())
        }

    @Test
    fun separate_wrappers_over_same_datastore_see_each_others_writes() =
        runBlocking {
            // DataStore permits only one instance per file per process, but two
            // TinkEncryptedBlobStorage wrappers can share a single DataStore. This
            // mirrors the production shape: one DataStore singleton, potentially
            // rebuilt wrappers around it.
            val dataStore = newDataStore(tempFolder.newFile("ds3.preferences_pb"))
            val payload = "persisted".toByteArray()

            TinkEncryptedBlobStorage(dataStore, aead).write(payload)
            val reopened = TinkEncryptedBlobStorage(dataStore, aead)
            assertArrayEquals(payload, reopened.read())
        }

    @Test
    fun decryption_under_a_different_key_yields_null() =
        runBlocking {
            val dataStore = newDataStore(tempFolder.newFile("ds4.preferences_pb"))
            TinkEncryptedBlobStorage(dataStore, aead).write("secret".toByteArray())

            val otherAead =
                KeysetHandle
                    .generateNew(KeyTemplates.get("AES256_GCM"))
                    .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            val tampered = TinkEncryptedBlobStorage(dataStore, otherAead)
            // Wrong key → Tink refuses to decrypt. We surface that as "no data" so
            // callers can fall back to an empty state rather than crashing.
            assertNull(tampered.read())
        }

    @Test
    fun on_disk_bytes_are_not_the_plaintext() =
        runBlocking {
            val file = tempFolder.newFile("ds5.preferences_pb")
            val payload = "ldk_server_remote plaintext probe".toByteArray()
            TinkEncryptedBlobStorage(newDataStore(file), aead).write(payload)

            // Sanity check that encryption actually ran — the raw file must not contain
            // the plaintext bytes verbatim.
            val fileBytes = file.readBytes()
            val fileAsString = String(fileBytes, Charsets.ISO_8859_1)
            assertNotEquals(0, fileAsString.length)
            assert(!fileAsString.contains("ldk_server_remote plaintext probe")) {
                "Expected ciphertext on disk, found plaintext substring"
            }
        }
}
