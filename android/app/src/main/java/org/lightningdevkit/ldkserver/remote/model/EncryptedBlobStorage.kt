package org.lightningdevkit.ldkserver.remote.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first

/**
 * Persists a single opaque byte blob to disk, encrypted at rest. The caller owns the
 * in-memory plaintext format — here we only round-trip bytes.
 *
 * Lives behind an interface so [ServerStore] can be unit-tested with a trivial
 * in-memory fake, without dragging in Android Keystore / Tink.
 */
interface EncryptedBlobStorage {
    /** Returns the stored plaintext, or `null` if nothing has been written yet. */
    suspend fun read(): ByteArray?

    /** Replaces the stored blob with [bytes]. */
    suspend fun write(bytes: ByteArray)
}

/**
 * [EncryptedBlobStorage] backed by a DataStore<Preferences> for on-disk storage and
 * Tink [Aead] (AES-256-GCM) for the encryption envelope. Tink's keyset is itself
 * wrapped by a hardware-backed Android Keystore key in production — see
 * [TinkEncryptedBlobStorage.create].
 *
 * [aead] is injected rather than built internally so unit tests can supply an
 * in-process Tink key instead of reaching into the Android Keystore.
 */
class TinkEncryptedBlobStorage(
    private val dataStore: DataStore<Preferences>,
    private val aead: Aead,
) : EncryptedBlobStorage {
    override suspend fun read(): ByteArray? {
        val sealed = dataStore.data.first()[KEY] ?: return null
        return runCatching { aead.decrypt(sealed, ASSOCIATED_DATA) }.getOrNull()
    }

    override suspend fun write(bytes: ByteArray) {
        val sealed = aead.encrypt(bytes, ASSOCIATED_DATA)
        dataStore.edit { prefs -> prefs[KEY] = sealed }
    }

    companion object {
        private val KEY = byteArrayPreferencesKey("servers_encrypted")

        // Binds ciphertexts to this storage's intended use; Tink refuses to decrypt
        // anything sealed under a different associated-data tag.
        private val ASSOCIATED_DATA = "ldk_server_remote.servers.v1".toByteArray()

        const val DATASTORE_NAME = "ldk_server_remote_servers"
        const val KEYSET_PREFS_NAME = "ldk_server_remote_keyset"
        const val KEYSET_NAME = "servers_keyset"
        const val MASTER_KEY_URI = "android-keystore://ldk_server_remote_masterkey"

        /**
         * Builds a production instance. The DataStore file and the Tink keyset
         * SharedPreferences are both app-private; the master key that wraps the
         * keyset lives in the hardware-backed Android Keystore.
         */
        fun create(
            context: Context,
            dataStore: DataStore<Preferences>,
        ): TinkEncryptedBlobStorage {
            AeadConfig.register()
            val keysetHandle =
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
            val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return TinkEncryptedBlobStorage(dataStore, aead)
        }

        /** Default DataStore file location for the production path. */
        fun defaultDataStoreFile(context: Context) = context.preferencesDataStoreFile(DATASTORE_NAME)
    }
}
