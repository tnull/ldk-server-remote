package org.lightningdevkit.ldkserver.remote.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork
import org.lightningdevkit.ldkserver.remote.model.ServerEntry
import org.lightningdevkit.ldkserver.remote.model.ServerStore
import org.lightningdevkit.ldkserver.remote.util.ServerUri

/**
 * State + actions for the add / edit server screen.
 *
 * Not an AndroidViewModel: takes the [ServerStore] as a constructor arg so it's
 * trivial to unit-test with an in-memory store.
 */
class AddOrEditServerViewModel(
    private val store: ServerStore,
    private val editId: String?,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {
    /** Observable UI state. A plain mutable state object keeps the screen code short. */
    var state: FormState by mutableStateOf(initialState())
        private set

    /** Apply every field from a scanned QR payload, preserving the existing id if editing. */
    fun applyScanned(fields: ServerUri.Fields) {
        state =
            state.copy(
                name = fields.name,
                network = fields.network,
                baseUrl = fields.baseUrl,
                apiKey = fields.apiKey,
                certificatePem = fields.certificatePem,
                scanInfo = "Pre-filled from QR. Review and adjust before saving.",
                validationError = null,
            )
    }

    fun onNameChange(value: String) {
        state = state.copy(name = value, validationError = null)
    }

    fun onNetworkChange(value: BitcoinNetwork) {
        state = state.copy(network = value)
    }

    fun onBaseUrlChange(value: String) {
        state = state.copy(baseUrl = value, validationError = null)
    }

    fun onApiKeyChange(value: String) {
        state = state.copy(apiKey = value, validationError = null)
    }

    fun onCertificatePemChange(value: String) {
        state = state.copy(certificatePem = value, validationError = null)
    }

    fun onScanError(reason: String) {
        state = state.copy(scanInfo = "Couldn't read QR: $reason", validationError = null)
    }

    /**
     * Validate and persist. Returns `true` iff the entry was saved. Callers (the
     * composable) use the return value to decide whether to pop the back stack.
     */
    fun save(): Boolean {
        val reason = validate(state)
        if (reason != null) {
            state = state.copy(validationError = reason)
            return false
        }
        val entry =
            if (editId != null) {
                val existing = store.get(editId)
                existing?.copy(
                    name = state.name.trim(),
                    network = state.network,
                    baseUrl = state.baseUrl.trim(),
                    apiKey = state.apiKey.trim(),
                    certificatePem = state.certificatePem,
                ) ?: return false.also {
                    state = state.copy(validationError = "This server was removed elsewhere.")
                }
            } else {
                ServerEntry(
                    id = ServerEntry.newId(),
                    name = state.name.trim(),
                    network = state.network,
                    baseUrl = state.baseUrl.trim(),
                    apiKey = state.apiKey.trim(),
                    certificatePem = state.certificatePem,
                    createdAtEpochSeconds = clock(),
                )
            }
        if (editId == null) store.add(entry) else store.update(entry)
        return true
    }

    private fun initialState(): FormState {
        val existing = editId?.let { store.get(it) }
        return if (existing != null) {
            FormState(
                name = existing.name,
                network = existing.network,
                baseUrl = existing.baseUrl,
                apiKey = existing.apiKey,
                certificatePem = existing.certificatePem,
            )
        } else {
            FormState()
        }
    }

    data class FormState(
        val name: String = "",
        val network: BitcoinNetwork = BitcoinNetwork.SIGNET,
        val baseUrl: String = "",
        val apiKey: String = "",
        val certificatePem: String = "",
        val scanInfo: String? = null,
        val validationError: String? = null,
    )

    companion object {
        /**
         * Returns the first validation error, or `null` if the form is saveable.
         * Kept as a pure function so the screen can surface the same check on its own.
         */
        fun validate(state: FormState): String? {
            if (state.name.isBlank()) return "Name is required"
            if (state.baseUrl.isBlank()) return "Server URL is required"
            if (state.baseUrl.startsWith("http://") || state.baseUrl.startsWith("https://")) {
                return "Server URL must not include a scheme (host:port only)"
            }
            if (state.apiKey.isBlank()) return "API key is required"
            if (!state.certificatePem.contains("BEGIN CERTIFICATE")) {
                return "TLS certificate must be a PEM block (-----BEGIN CERTIFICATE-----)"
            }
            return null
        }
    }
}
