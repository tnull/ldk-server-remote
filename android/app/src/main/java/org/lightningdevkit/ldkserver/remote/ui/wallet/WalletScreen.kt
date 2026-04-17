package org.lightningdevkit.ldkserver.remote.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.ui.AppState
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.ui.wallet.components.ActionButtons
import org.lightningdevkit.ldkserver.remote.ui.wallet.components.BalanceCard
import org.lightningdevkit.ldkserver.remote.ui.wallet.components.PaymentListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    appState: AppState,
    serverId: String,
    onSendClicked: () -> Unit = {},
    onReceiveClicked: () -> Unit = {},
) {
    // Lazy-construct a per-server VM. `remember(serverId)` scopes it to the active
    // server — switching servers disposes the old VM rather than reusing its cached
    // balances/payments against the new endpoint.
    val viewModel =
        remember(serverId) {
            val service =
                appState.serviceFor(serverId)
                    ?: error("No server configured for id $serverId")
            WalletViewModel(service)
        }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(message = it)
            viewModel.consumeError()
        }
    }

    WalletScreenContent(
        state = state,
        snackbar = snackbar,
        onRefresh = { viewModel.refresh(isInitial = false) },
        onSendClicked = onSendClicked,
        onReceiveClicked = onReceiveClicked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletScreenContent(
    state: WalletUiState,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onSendClicked: () -> Unit,
    onReceiveClicked: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
    ) { innerPadding ->
        when {
            state.isLoading && !state.hasData ->
                CenteredLoading(modifier = Modifier.padding(innerPadding))
            !state.hasData ->
                NoDataYet(modifier = Modifier.padding(innerPadding))
            else ->
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                ) {
                    PopulatedWallet(
                        state = state,
                        onSendClicked = onSendClicked,
                        onReceiveClicked = onReceiveClicked,
                    )
                }
        }
    }
}

@Composable
private fun PopulatedWallet(
    state: WalletUiState,
    onSendClicked: () -> Unit,
    onReceiveClicked: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val balances = state.balances ?: return@item
            BalanceCard(
                totalSats = state.totalSats,
                onchainSats = balances.totalOnchainBalanceSats,
                lightningSats = balances.totalLightningBalanceSats,
            )
        }
        item {
            ActionButtons(
                onSendClicked = onSendClicked,
                onReceiveClicked = onReceiveClicked,
            )
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.payments.isEmpty()) {
            item {
                Text(
                    text = "No payments yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.payments, key = { it.id }) { payment ->
                PaymentListItem(payment = payment)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoDataYet(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't reach the server yet.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Pull down to retry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun WalletScreenPreview() {
    LdkServerRemoteTheme {
        WalletScreenContent(
            state =
                WalletUiState(
                    isLoading = false,
                    balances =
                        org.lightningdevkit.ldkserver.client.BalanceInfo(
                            totalOnchainBalanceSats = 1_000_000UL,
                            spendableOnchainBalanceSats = 950_000UL,
                            totalAnchorChannelsReserveSats = 50_000UL,
                            totalLightningBalanceSats = 2_345_678UL,
                        ),
                    payments = emptyList(),
                ),
            snackbar = remember { SnackbarHostState() },
            onRefresh = {},
            onSendClicked = {},
            onReceiveClicked = {},
        )
    }
}
