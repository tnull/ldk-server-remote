package org.lightningdevkit.ldkserver.remote.model

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the glue in [EncryptedServerStoreMigration]. The real
 * [EncryptedServerStoreMigration.RealLegacyServersReader] touches AndroidX Security
 * Crypto + Keystore, which is painful to stand up in a JVM test — so we pin that path
 * behind the [EncryptedServerStoreMigration.LegacyServersReader] seam and exercise
 * the migration logic itself with a fake.
 */
class EncryptedServerStoreMigrationTest {
    private val fakeContext: Context = mockk(relaxed = true)

    @Test
    fun copies_legacy_json_into_empty_new_storage() =
        runBlocking {
            val legacy = FakeLegacyReader(payload = """[{"name":"old"}]""")
            val newStorage = InMemoryEncryptedBlobStorageForMigration()

            EncryptedServerStoreMigration.migrateIfNeeded(fakeContext, newStorage, legacy)

            assertArrayEquals(
                """[{"name":"old"}]""".toByteArray(Charsets.UTF_8),
                newStorage.read(),
            )
            assertEquals(1, legacy.cleanupCallCount)
        }

    @Test
    fun does_not_overwrite_non_empty_new_storage() =
        runBlocking {
            val legacy = FakeLegacyReader(payload = "legacy")
            val newStorage =
                InMemoryEncryptedBlobStorageForMigration().apply { write("new".toByteArray()) }

            EncryptedServerStoreMigration.migrateIfNeeded(fakeContext, newStorage, legacy)

            // New data wins; legacy is only a fallback.
            assertArrayEquals("new".toByteArray(), newStorage.read())
            // But we still clean up the legacy file — stale data shouldn't linger on disk.
            assertEquals(1, legacy.cleanupCallCount)
        }

    @Test
    fun no_legacy_data_is_a_noop() =
        runBlocking {
            val legacy = FakeLegacyReader(payload = null)
            val newStorage = InMemoryEncryptedBlobStorageForMigration()

            EncryptedServerStoreMigration.migrateIfNeeded(fakeContext, newStorage, legacy)

            assertNull(newStorage.read())
            // Nothing to clean up — leaves the (presumably fresh-install) device alone.
            assertEquals(0, legacy.cleanupCallCount)
        }

    @Test
    fun second_invocation_after_successful_migration_is_a_noop() =
        runBlocking {
            val legacy = FakeLegacyReader(payload = "payload")
            val newStorage = InMemoryEncryptedBlobStorageForMigration()

            EncryptedServerStoreMigration.migrateIfNeeded(fakeContext, newStorage, legacy)
            // First pass cleaned up, so the fake now reports "no legacy data".
            legacy.payload = null
            EncryptedServerStoreMigration.migrateIfNeeded(fakeContext, newStorage, legacy)

            assertArrayEquals("payload".toByteArray(), newStorage.read())
            assertTrue(
                "cleanup should not run again once the legacy blob is gone",
                legacy.cleanupCallCount == 1,
            )
        }

    private class FakeLegacyReader(
        var payload: String?,
    ) : EncryptedServerStoreMigration.LegacyServersReader {
        var cleanupCallCount = 0

        override fun read(context: Context): String? = payload

        override fun cleanup(context: Context) {
            cleanupCallCount++
            payload = null
        }
    }

    private class InMemoryEncryptedBlobStorageForMigration : EncryptedBlobStorage {
        @Volatile private var blob: ByteArray? = null

        override suspend fun read(): ByteArray? = blob?.copyOf()

        override suspend fun write(bytes: ByteArray) {
            blob = bytes.copyOf()
        }
    }
}
