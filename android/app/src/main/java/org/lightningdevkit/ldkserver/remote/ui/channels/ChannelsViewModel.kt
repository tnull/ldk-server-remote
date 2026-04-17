package org.lightningdevkit.ldkserver.remote.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.remote.service.LdkService

/**
 * State for the Channels tab. Fetches `list_channels` on init and on [refresh].
 * Also owns the close / force-close / open actions; successful mutations trigger
 * an automatic re-fetch so the list reflects the latest state.
 */
class ChannelsViewModel(
    private val service: LdkService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = mutableState.asStateFlow()

    init {
        refresh(isInitial = true)
    }

    fun refresh(isInitial: Boolean = false) {
        mutableState.update {
            it.copy(isLoading = isInitial, isRefreshing = !isInitial, errorMessage = null)
        }
        viewModelScope.launch {
            val outcome = runCatching { service.listChannels() }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    channels = outcome.getOrNull() ?: it.channels,
                    errorMessage = outcome.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** Open a new channel. On success, refreshes the list. */
    fun openChannel(
        nodePubkey: String,
        address: String,
        amountSats: ULong,
        announceChannel: Boolean,
        onDone: (Result<String>) -> Unit,
    ) {
        mutableState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            val outcome =
                runCatching {
                    service.openChannel(
                        nodePubkey = nodePubkey,
                        address = address,
                        channelAmountSats = amountSats,
                        pushToCounterpartyMsat = null,
                        announceChannel = announceChannel,
                    )
                }
            mutableState.update { it.copy(isMutating = false) }
            onDone(outcome)
            if (outcome.isSuccess) refresh(isInitial = false)
        }
    }

    fun closeChannel(
        channel: ChannelInfo,
        force: Boolean,
        reason: String? = null,
        onDone: (Result<Unit>) -> Unit,
    ) {
        mutableState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            val outcome =
                runCatching {
                    if (force) {
                        service.forceCloseChannel(
                            userChannelId = channel.userChannelId,
                            counterpartyNodeId = channel.counterpartyNodeId,
                            forceCloseReason = reason,
                        )
                    } else {
                        service.closeChannel(
                            userChannelId = channel.userChannelId,
                            counterpartyNodeId = channel.counterpartyNodeId,
                        )
                    }
                }
            mutableState.update { it.copy(isMutating = false) }
            onDone(outcome)
            if (outcome.isSuccess) refresh(isInitial = false)
        }
    }

    fun consumeError() {
        mutableState.update { it.copy(errorMessage = null) }
    }
}

data class ChannelsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val channels: List<ChannelInfo> = emptyList(),
    val errorMessage: String? = null,
)
