package org.lightningdevkit.ldkserver.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.lightningdevkit.ldkserver.remote.model.ServerEntry
import org.lightningdevkit.ldkserver.remote.ui.channels.ChannelsScreen
import org.lightningdevkit.ldkserver.remote.ui.navigation.Routes
import org.lightningdevkit.ldkserver.remote.ui.node.NodeScreen
import org.lightningdevkit.ldkserver.remote.ui.wallet.WalletScreen

/**
 * Per-server shell. Shown after the user taps a server in the server list. Renders a
 * top bar with the server name + a back arrow, a bottom nav bar with Wallet / Channels
 * / Node tabs, and switches content based on the selected tab.
 *
 * Send/Receive and other per-feature flows inside a tab will get their own nested
 * NavHost in later steps (Steps 13–14); for now each tab renders a placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    appState: AppState,
    serverId: String,
    onBackToServerList: () -> Unit,
) {
    val server: ServerEntry? = remember(serverId) { appState.serverStore.get(serverId) }
    if (server == null) {
        ServerMissingScreen(onBackToServerList = onBackToServerList)
        return
    }

    var selectedTab by remember { mutableStateOf(Routes.Tab.WALLET) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = server.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = server.network.displayName(),
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToServerList) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to servers")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.route,
                        onClick = { selectedTab = tab.route },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            when (selectedTab) {
                Routes.Tab.WALLET ->
                    WalletScreen(
                        appState = appState,
                        serverId = serverId,
                        // Send/Receive screens land in Steps 13 and 14. Until then the
                        // buttons are inert — the Wallet tab is already useful as a
                        // balance + activity view on its own.
                        onSendClicked = {},
                        onReceiveClicked = {},
                    )
                Routes.Tab.CHANNELS -> ChannelsScreen(appState = appState, serverId = serverId)
                Routes.Tab.NODE -> NodeScreen(appState = appState, serverId = serverId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerMissingScreen(onBackToServerList: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server not found") },
                navigationIcon = {
                    IconButton(onClick = onBackToServerList) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to servers")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("This server was removed. Go back to the server list.")
        }
    }
}

private enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    WALLET(Routes.Tab.WALLET, "Wallet", Icons.Filled.AccountBalanceWallet),
    CHANNELS(Routes.Tab.CHANNELS, "Channels", Icons.Filled.Hub),
    NODE(Routes.Tab.NODE, "Node", Icons.Filled.Dns),
}
