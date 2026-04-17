package org.lightningdevkit.ldkserver.remote.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.remote.ui.AppState
import org.lightningdevkit.ldkserver.remote.ui.channels.components.ChannelListItem
import org.lightningdevkit.ldkserver.remote.ui.common.Peeker

/**
 * Channels tab: list of channels with pull-to-refresh + "+" FAB to open, plus an
 * expandable detail card per row for close / force-close. Opening and closing flows
 * are handled as modal overlays (like Send/Receive) so the back stack stays simple.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    appState: AppState,
    serverId: String,
) {
    val service =
        remember(serverId) {
            appState.serviceFor(serverId) ?: error("No server configured for id $serverId")
        }
    val viewModel = remember(serverId) { ChannelsViewModel(service) }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showOpen by remember { mutableStateOf(false) }
    var detailChannel by remember { mutableStateOf<ChannelInfo?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    when {
        showOpen ->
            OpenChannelScreen(
                viewModel = viewModel,
                onDismiss = { showOpen = false },
            )
        detailChannel != null ->
            ChannelDetailScreen(
                channel = detailChannel!!,
                viewModel = viewModel,
                onDismiss = { detailChannel = null },
            )
        else ->
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
                floatingActionButton = {
                    if (state.channels.isNotEmpty()) {
                        FloatingActionButton(onClick = { showOpen = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Open channel")
                        }
                    }
                },
            ) { innerPadding ->
                when {
                    state.isLoading && state.channels.isEmpty() ->
                        LoadingState(modifier = Modifier.padding(innerPadding))
                    state.channels.isEmpty() ->
                        EmptyState(
                            modifier = Modifier.padding(innerPadding),
                            onOpenClicked = { showOpen = true },
                        )
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
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.channels, key = { it.channelId }) { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        onClick = { detailChannel = channel },
                                    )
                                }
                            }
                        }
                }
            }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onOpenClicked: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No channels yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open a Lightning channel to start sending and receiving payments off-chain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            ExtendedFloatingActionButton(
                onClick = onOpenClicked,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Open channel") },
            )
        }
        Peeker()
    }
}
