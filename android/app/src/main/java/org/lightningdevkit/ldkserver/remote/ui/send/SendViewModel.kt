package org.lightningdevkit.ldkserver.remote.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.DecodedInvoice
import org.lightningdevkit.ldkserver.client.DecodedOffer
import org.lightningdevkit.ldkserver.client.UnifiedSendResult
import org.lightningdevkit.ldkserver.remote.service.LdkService

/**
 * State machine for the Send flow.
 *
 * Three phases:
 *  - [SendStep.Input]: the user enters / scans a payment URI and an optional amount.
 *  - [SendStep.Confirm]: we've parsed (or at least detected) the input and show a
 *    summary for the user to confirm before firing `unified_send`.
 *  - [SendStep.Result]: the send has completed; we show txid / payment id on success
 *    or an error on failure, and the user taps Done to close the flow.
 *
 * `unified_send` is the primary send method because it transparently dispatches across
 * on-chain, BOLT11, BOLT12, BIP21, and BIP353 — the user experience is "paste or scan
 * anything that looks like a Bitcoin payment destination."
 */
class SendViewModel(
    private val service: LdkService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = mutableState.asStateFlow()

    fun onInputChange(value: String) {
        mutableState.update {
            it.copy(input = value, validationError = null)
        }
    }

    fun onAmountSatsChange(value: String) {
        // Strip anything that isn't a digit; this lets paste-with-formatting
        // accidents ("1,000 sats") degrade gracefully instead of producing a
        // validation error the user has to parse.
        mutableState.update {
            it.copy(amountSatsInput = value.filter { ch -> ch.isDigit() }, validationError = null)
        }
    }

    /** Scanned QR → fills the input field. No decode attempt here; that happens on Next. */
    fun applyScanned(text: String) {
        mutableState.update {
            it.copy(input = text.trim(), validationError = null, decodeHint = null)
        }
    }

    /**
     * Move from Input → Confirm. Validates, optionally runs a best-effort decode
     * (so we can show the user the amount + description before sending), and flips
     * the step.
     */
    fun onNextClick() {
        val current = mutableState.value
        val input = current.input.trim()
        if (input.isEmpty()) {
            mutableState.update { it.copy(validationError = "Enter a payment destination or scan a QR code.") }
            return
        }
        val amount = parseAmountOrNull(current.amountSatsInput)
        if (current.amountSatsInput.isNotBlank() && amount == null) {
            mutableState.update { it.copy(validationError = "Amount must be a positive whole number of sats.") }
            return
        }
        mutableState.update {
            it.copy(
                input = input,
                amountSats = amount,
                validationError = null,
                step = SendStep.Confirm,
                decodedInvoice = null,
                decodedOffer = null,
                decodeHint = null,
            )
        }
        viewModelScope.launch {
            // Best-effort decode. Any failure is silently swallowed — `unified_send`
            // on the server can still handle on-chain addresses, BIP21 URIs, and
            // BIP353 names that our local decoders won't touch.
            val tryInvoice = runCatching { service.decodeInvoice(input) }.getOrNull()
            if (tryInvoice != null) {
                mutableState.update { it.copy(decodedInvoice = tryInvoice) }
                return@launch
            }
            val tryOffer = runCatching { service.decodeOffer(input) }.getOrNull()
            if (tryOffer != null) {
                mutableState.update { it.copy(decodedOffer = tryOffer) }
                return@launch
            }
            mutableState.update {
                it.copy(decodeHint = "Couldn't decode locally — the server will parse this.")
            }
        }
    }

    /** Execute the send. */
    fun onSendClick() {
        val current = mutableState.value
        if (current.isSending) return
        mutableState.update { it.copy(isSending = true, sendError = null) }
        viewModelScope.launch {
            val outcome =
                runCatching {
                    service.unifiedSend(
                        uri = current.input,
                        amountMsat = current.amountSats?.let { it * 1_000UL },
                    )
                }
            mutableState.update {
                it.copy(
                    isSending = false,
                    step = SendStep.Result,
                    result = outcome.getOrNull(),
                    sendError = outcome.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** Return to the Input step. */
    fun onBackToInput() {
        mutableState.update {
            it.copy(
                step = SendStep.Input,
                decodedInvoice = null,
                decodedOffer = null,
                decodeHint = null,
                validationError = null,
            )
        }
    }

    /** After viewing a success/failure result, reset so the flow can be re-entered fresh. */
    fun onDismissResult() {
        mutableState.value = SendUiState()
    }

    private fun parseAmountOrNull(raw: String): ULong? {
        if (raw.isBlank()) return null
        return raw.toULongOrNull()?.takeIf { it > 0UL }
    }
}

enum class SendStep { Input, Confirm, Result }

data class SendUiState(
    val step: SendStep = SendStep.Input,
    val input: String = "",
    val amountSatsInput: String = "",
    val amountSats: ULong? = null,
    val decodedInvoice: DecodedInvoice? = null,
    val decodedOffer: DecodedOffer? = null,
    val decodeHint: String? = null,
    val validationError: String? = null,
    val isSending: Boolean = false,
    val result: UnifiedSendResult? = null,
    val sendError: String? = null,
)
