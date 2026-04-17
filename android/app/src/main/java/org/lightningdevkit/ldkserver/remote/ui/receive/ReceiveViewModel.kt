package org.lightningdevkit.ldkserver.remote.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.remote.service.LdkService

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
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = mutableState.asStateFlow()

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
                        ReceiveType.ONCHAIN -> service.onchainReceive()
                        ReceiveType.BOLT11 ->
                            service
                                .bolt11Receive(
                                    amountMsat = snap.amountSatsInput.toULongOrNull()?.let { it * 1_000UL },
                                    description = snap.description.ifBlank { null },
                                    expirySecs = snap.expirySecsInput.toUIntOrNull() ?: 3_600u,
                                ).invoice
                        ReceiveType.BOLT12 ->
                            service
                                .bolt12Receive(
                                    description = snap.description,
                                    amountMsat = snap.amountSatsInput.toULongOrNull()?.let { it * 1_000UL },
                                    expirySecs = snap.expirySecsInput.toUIntOrNull(),
                                    quantity = null,
                                ).offer
                    }
                }
            mutableState.update {
                it.copy(
                    isGenerating = false,
                    generatedPayload = outcome.getOrNull(),
                    error = outcome.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun onBackFromForm() {
        mutableState.update { it.copy(step = ReceiveStep.TypePicker, error = null) }
    }

    fun onBackFromQr() {
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
        mutableState.value = ReceiveUiState()
    }
}

enum class ReceiveStep { TypePicker, Form, Qr }

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
)
