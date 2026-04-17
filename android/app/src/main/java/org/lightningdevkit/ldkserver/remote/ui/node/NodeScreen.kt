package org.lightningdevkit.ldkserver.remote.ui.node

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.PeerInfo
import org.lightningdevkit.ldkserver.remote.ui.AppState
import org.lightningdevkit.ldkserver.remote.util.TimeFormatter

/**
 * Node tab: identity + sync status + peers. Tap a copyable field to copy to the
 * clipboard. Peer list supports connect (+FAB) and disconnect (tap row).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen(
    appState: AppState,
    serverId: String,
) {
    val service =
        remember(serverId) {
            appState.serviceFor(serverId) ?: error("No server configured for id $serverId")
        }
    val viewModel = remember(serverId) { NodeViewModel(service) }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showConnect by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    if (showConnect) {
        ConnectPeerScreen(viewModel = viewModel, onDismiss = { showConnect = false })
        return
    }

    val copyToClipboard: (String, String) -> Unit = { label, value ->
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { snackbar.showSnackbar("$label copied") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        floatingActionButton = {
            FloatingActionButton(onClick = { showConnect = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Connect peer")
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.nodeInfo == null ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            else ->
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh(isInitial = false) },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.nodeInfo?.let { info ->
                            item { NodeInfoCard(info, onCopy = copyToClipboard) }
                            item { SyncStatusCard(info) }
                        }
                        item {
                            SectionHeader("Peers")
                        }
                        if (state.peers.isEmpty()) {
                            item {
                                Text(
                                    "No peers connected.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.peers, key = { it.nodeId }) { peer ->
                                PeerRow(
                                    peer = peer,
                                    onDisconnect = {
                                        viewModel.disconnectPeer(peer.nodeId) { outcome ->
                                            outcome.onFailure {
                                                scope.launch {
                                                    snackbar.showSnackbar(it.message ?: "Failed to disconnect")
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun NodeInfoCard(
    info: NodeInfo,
    onCopy: (String, String) -> Unit,
) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                info.nodeAlias ?: "Unnamed node",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            CopyableField("Node ID", info.nodeId, onCopy = onCopy)

            if (info.nodeUris.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Connection strings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info.nodeUris.forEach { uri ->
                    Spacer(Modifier.height(4.dp))
                    CopyableField(label = "", value = uri, onCopy = onCopy)
                }
            }

            info.currentBestBlock?.let { bb ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "Best block ${bb.height}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    truncateMiddle(bb.blockHash, keep = 10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                truncateMiddle(value, keep = 14),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = { onCopy(label.ifBlank { "Value" }, value) }) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SyncStatusCard(info: NodeInfo) {
    OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sync status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SyncRow("Lightning wallet", info.latestLightningWalletSyncTimestamp)
            SyncRow("On-chain wallet", info.latestOnchainWalletSyncTimestamp)
            SyncRow("Fee rate cache", info.latestFeeRateCacheUpdateTimestamp)
            SyncRow("RGS snapshot", info.latestRgsSnapshotTimestamp)
            SyncRow("Node announcement", info.latestNodeAnnouncementBroadcastTimestamp)
        }
    }
}

@Composable
private fun SyncRow(
    label: String,
    timestamp: ULong?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = statusColorFor(timestamp)
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            timestamp?.let { TimeFormatter.relativeTime(it) } ?: "never",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusColorFor(ts: ULong?): Color {
    if (ts == null) return Color(0xFF9E9E9E)
    val now = System.currentTimeMillis() / 1000
    val age = now - ts.toLong()
    return when {
        age < 60 * 15 -> Color(0xFF2FA44F) // green, fresh
        age < 60 * 60 -> Color(0xFFE2B007) // amber, stale
        else -> Color(0xFFD32F2F) // red, very stale
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PeerRow(
    peer: PeerInfo,
    onDisconnect: () -> Unit,
) {
    Card(
        onClick = onDisconnect,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(
                            if (peer.isConnected) Color(0xFF2FA44F) else Color(0xFF9E9E9E),
                            CircleShape,
                        ),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    truncateMiddle(peer.nodeId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    peer.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peer.isPersisted) {
                Text(
                    "Persisted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun truncateMiddle(
    s: String,
    keep: Int = 8,
): String = if (s.length <= 2 * keep + 1) s else s.take(keep) + "…" + s.takeLast(keep)
