package org.lightningdevkit.ldkserver.remote.model

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * One-shot migration from the pre-existing `EncryptedSharedPreferences`-backed storage
 * to the new DataStore + Tink path. Runs inline during [EncryptedServerStore.create].
 *
 * The flow, on every app start:
 *  1. Ask a [LegacyServersReader] whether a legacy blob still exists.
 *  2. If yes and the new storage is empty, copy the blob across. The new storage taking
 *     priority means a user who has already edited data in the new version won't have
 *     it silently clobbered by stale legacy state.
 *  3. Delete the legacy SharedPreferences file and the AndroidX master key alias, so
 *     subsequent launches short-circuit immediately.
 *
 * This whole file can be deleted once the migration window closes — the removal also
 * allows dropping the `androidx.security:security-crypto` dependency.
 */
object EncryptedServerStoreMigration {
    const val LEGACY_PREFS_FILE_NAME = "ldk_server_remote_servers"
    const val LEGACY_SERVERS_KEY = "servers"

    // AndroidX Security Crypto's default master-key alias. We delete it after migration
    // so the Keystore entry doesn't linger after its only consumer is gone.
    const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"

    suspend fun migrateIfNeeded(
        context: Context,
        newStorage: EncryptedBlobStorage,
        legacyReader: LegacyServersReader = RealLegacyServersReader,
    ) {
        val legacyJson = legacyReader.read(context)
        if (legacyJson != null && newStorage.read() == null) {
            newStorage.write(legacyJson.toByteArray(Charsets.UTF_8))
        }
        if (legacyJson != null) {
            // Only clean up once we know the legacy path had real data — avoids
            // stamping on unrelated files or Keystore aliases on a fresh install.
            legacyReader.cleanup(context)
        }
    }

    /**
     * Indirection over the legacy EncryptedSharedPreferences so tests can exercise the
     * migration logic without standing up AndroidX Security + Keystore in Robolectric.
     */
    interface LegacyServersReader {
        fun read(context: Context): String?

        fun cleanup(context: Context)
    }

    /** Production [LegacyServersReader] that actually touches the old SharedPreferences file. */
    object RealLegacyServersReader : LegacyServersReader {
        override fun read(context: Context): String? {
            if (!legacyPrefsFile(context).exists()) return null
            return runCatching {
                val masterKey =
                    MasterKey
                        .Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                val prefs =
                    EncryptedSharedPreferences.create(
                        context,
                        LEGACY_PREFS_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                prefs.getString(LEGACY_SERVERS_KEY, null)
            }.getOrNull()
        }

        override fun cleanup(context: Context) {
            legacyPrefsFile(context).delete()
            runCatching {
                val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keystore.containsAlias(LEGACY_MASTER_KEY_ALIAS)) {
                    keystore.deleteEntry(LEGACY_MASTER_KEY_ALIAS)
                }
            }
        }

        private fun legacyPrefsFile(context: Context): File = File(context.filesDir.parentFile, "shared_prefs/$LEGACY_PREFS_FILE_NAME.xml")
    }
}
