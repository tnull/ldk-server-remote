package org.lightningdevkit.ldkserver.remote.ui.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.PeerInfo
import org.lightningdevkit.ldkserver.remote.service.LdkService

/**
 * State for the Node tab. Holds the result of `get_node_info` + `list_peers` in a
 * single snapshot — both are cheap, we fetch them together. Mutations (connect /
 * disconnect peer) trigger a re-fetch on success.
 */
class NodeViewModel(
    private val service: LdkService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NodeUiState())
    val state: StateFlow<NodeUiState> = mutableState.asStateFlow()

    init {
        refresh(isInitial = true)
    }

    fun refresh(isInitial: Boolean = false) {
        mutableState.update {
            it.copy(isLoading = isInitial, isRefreshing = !isInitial, errorMessage = null)
        }
        viewModelScope.launch {
            val nodeOutcome = runCatching { service.getNodeInfo() }
            val peersOutcome = runCatching { service.listPeers() }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    nodeInfo = nodeOutcome.getOrNull() ?: it.nodeInfo,
                    peers = peersOutcome.getOrNull() ?: it.peers,
                    errorMessage =
                        (peersOutcome.exceptionOrNull() ?: nodeOutcome.exceptionOrNull())
                            ?.message,
                )
            }
        }
    }

    fun connectPeer(
        nodePubkey: String,
        address: String,
        persist: Boolean,
        onDone: (Result<Unit>) -> Unit,
    ) {
        mutableState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            val outcome = runCatching { service.connectPeer(nodePubkey, address, persist) }
            mutableState.update { it.copy(isMutating = false) }
            onDone(outcome)
            if (outcome.isSuccess) refresh(isInitial = false)
        }
    }

    fun disconnectPeer(
        nodePubkey: String,
        onDone: (Result<Unit>) -> Unit,
    ) {
        mutableState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            val outcome = runCatching { service.disconnectPeer(nodePubkey) }
            mutableState.update { it.copy(isMutating = false) }
            onDone(outcome)
            if (outcome.isSuccess) refresh(isInitial = false)
        }
    }

    fun consumeError() {
        mutableState.update { it.copy(errorMessage = null) }
    }
}

data class NodeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val nodeInfo: NodeInfo? = null,
    val peers: List<PeerInfo> = emptyList(),
    val errorMessage: String? = null,
)
