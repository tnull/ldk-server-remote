package org.lightningdevkit.ldkserver.remote.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PaymentKindInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.remote.service.LdkService
import kotlin.coroutines.coroutineContext

/**
 * State machine for the Receive flow.
 *
 * Three steps:
 *  - [ReceiveStep.TypePicker]: choose on-chain / BOLT11 / BOLT12.
 *  - [ReceiveStep.Form]: per-type amount + description entry.
 *  - [ReceiveStep.Qr]: display the generated payload as a QR code with copy / share
 *    affordances.
 *
 * Per the Bitcoin Design Guide, the screen never displays the wallet balance — that
 * privacy detail belongs to the wallet tab, not the "show this to the sender" view.
 */
class ReceiveViewModel(
    private val service: LdkService,
    private val pollIntervalMillis: Long = 3_000L,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = mutableState.asStateFlow()

    private var pollJob: Job? = null

    fun onTypeChosen(type: ReceiveType) {
        mutableState.update {
            it.copy(
                type = type,
                step = if (type == ReceiveType.ONCHAIN) ReceiveStep.Qr else ReceiveStep.Form,
                error = null,
            )
        }
        if (type == ReceiveType.ONCHAIN) generate()
    }

    fun onAmountSatsChange(value: String) {
        mutableState.update {
            it.copy(amountSatsInput = value.filter { ch -> ch.isDigit() }, error = null)
        }
    }

    fun onDescriptionChange(value: String) {
        mutableState.update { it.copy(description = value, error = null) }
    }

    fun onExpirySecsChange(value: String) {
        mutableState.update {
            it.copy(expirySecsInput = value.filter { ch -> ch.isDigit() }, error = null)
        }
    }

    /** Fire the appropriate receive RPC for the currently-selected type. */
    fun generate() {
        val snap = mutableState.value
        val type = snap.type ?: return
        mutableState.update { it.copy(isGenerating = true, error = null, step = ReceiveStep.Qr) }
        viewModelScope.launch {
            val outcome =
                runCatching {
                    when (type) {
                        ReceiveType.ONCHAIN -> GeneratedPayload(payload = service.onchainReceive(), matcher = null)
                        ReceiveType.BOLT11 -> {
                            val result =
                                service.bolt11Receive(
                                    amountMsat = snap.amountSatsInput.toULongOrNull()?.let { it * 1_000UL },
                                    description = snap.description.ifBlank { null },
                                    expirySecs = snap.expirySecsInput.toUIntOrNull() ?: 3_600u,
                                )
                            GeneratedPayload(
                                payload = result.invoice,
                                matcher = PaymentMatcher.Bolt11Hash(result.paymentHash),
                            )
                        }
                        ReceiveType.BOLT12 -> {
                            val result =
                                service.bolt12Receive(
                                    description = snap.description,
                                    amountMsat = snap.amountSatsInput.toULongOrNull()?.let { it * 1_000UL },
                                    expirySecs = snap.expirySecsInput.toUIntOrNull(),
                                    quantity = null,
                                )
                            GeneratedPayload(
                                payload = result.offer,
                                matcher = PaymentMatcher.Bolt12OfferId(result.offerId),
                            )
                        }
                    }
                }
            mutableState.update {
                it.copy(
                    isGenerating = false,
                    generatedPayload = outcome.getOrNull()?.payload,
                    error = outcome.exceptionOrNull()?.message,
                )
            }
            // If we got a matcher (Lightning only — on-chain payments can't be correlated
            // to the generated address from the PaymentInfo kind), start watching for an
            // inbound success. Polls until the coroutine is cancelled (by reset() or by
            // the VM being cleared).
            outcome.getOrNull()?.matcher?.let(::startPolling)
        }
    }

    private fun startPolling(matcher: PaymentMatcher) {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (coroutineContext.isActive) {
                    delay(pollIntervalMillis)
                    val match =
                        runCatching { service.listPayments(pageToken = null).payments }
                            .getOrNull()
                            ?.firstOrNull { it.matches(matcher) }
                    if (match != null) {
                        mutableState.update {
                            it.copy(
                                step = ReceiveStep.Received,
                                receivedPayment = match,
                            )
                        }
                        return@launch
                    }
                }
            }
    }

    fun onBackFromForm() {
        mutableState.update { it.copy(step = ReceiveStep.TypePicker, error = null) }
    }

    fun onBackFromQr() {
        // The user's abandoning the current invoice/offer, so stop the background
        // poll. A new one starts if they regenerate.
        pollJob?.cancel()
        pollJob = null
        val prev = if (mutableState.value.type == ReceiveType.ONCHAIN) ReceiveStep.TypePicker else ReceiveStep.Form
        mutableState.update {
            it.copy(
                step = prev,
                generatedPayload = null,
                error = null,
            )
        }
    }

    fun reset() {
        pollJob?.cancel()
        pollJob = null
        mutableState.value = ReceiveUiState()
    }
}

enum class ReceiveStep { TypePicker, Form, Qr, Received }

enum class ReceiveType { ONCHAIN, BOLT11, BOLT12 }

data class ReceiveUiState(
    val step: ReceiveStep = ReceiveStep.TypePicker,
    val type: ReceiveType? = null,
    val amountSatsInput: String = "",
    val description: String = "",
    val expirySecsInput: String = "3600",
    val isGenerating: Boolean = false,
    val generatedPayload: String? = null,
    val error: String? = null,
    val receivedPayment: PaymentInfo? = null,
)

/** Internal pairing of a generated payload with the predicate to recognise a matching inbound payment. */
private data class GeneratedPayload(
    val payload: String,
    val matcher: PaymentMatcher?,
)

/**
 * How to tell whether a freshly-observed inbound payment matches the invoice / offer we
 * just generated.
 *
 * On-chain has no matcher: `PaymentKindInfo.Onchain` only carries the resulting txid,
 * not the receiving address, so there's no stable correlation — we simply don't poll
 * for on-chain.
 */
private sealed class PaymentMatcher {
    data class Bolt11Hash(val hash: String) : PaymentMatcher()

    data class Bolt12OfferId(val offerId: String) : PaymentMatcher()
}

private fun PaymentInfo.matches(matcher: PaymentMatcher): Boolean {
    if (direction != PaymentDirection.INBOUND) return false
    if (status != PaymentStatus.SUCCEEDED) return false
    return when (matcher) {
        is PaymentMatcher.Bolt11Hash ->
            when (val k = kind) {
                is PaymentKindInfo.Bolt11 -> k.hash == matcher.hash
                is PaymentKindInfo.Bolt11Jit -> k.hash == matcher.hash
                else -> false
            }
        is PaymentMatcher.Bolt12OfferId ->
            (kind as? PaymentKindInfo.Bolt12Offer)?.offerId == matcher.offerId
    }
}
