package org.lightningdevkit.ldkserver.remote.ui.servers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork
import org.lightningdevkit.ldkserver.remote.model.InMemoryServerStore
import org.lightningdevkit.ldkserver.remote.model.ServerEntry
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.ui.AppState
import org.lightningdevkit.ldkserver.remote.ui.TestAppState
import org.lightningdevkit.ldkserver.remote.ui.common.Peeker
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.ui.theme.networkColor

/**
 * Landing screen. Shows the list of configured servers; tapping a row enters that
 * server's main tabbed UI. The "+" FAB opens the add-server screen. Long-pressing a
 * row exposes edit / delete actions.
 *
 * Adding a server does NOT trigger a connection — connection is lazy and happens when
 * the user taps into the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    appState: AppState,
    onServerSelected: (ServerEntry) -> Unit,
    onAddServerClicked: () -> Unit,
    onEditServerClicked: (ServerEntry) -> Unit,
) {
    val servers by appState.servers.collectAsState()
    var pendingDelete by remember { mutableStateOf<ServerEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("LDK Server Remote") })
        },
        floatingActionButton = {
            if (servers.isNotEmpty()) {
                FloatingActionButton(onClick = onAddServerClicked) {
                    Icon(Icons.Filled.Add, contentDescription = "Add server")
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (servers.isEmpty()) {
                EmptyState(onAddServerClicked = onAddServerClicked)
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Peeker renders first so it sits *behind* the list. The list's
                    // cards cover it where there are rows; below the last row there's
                    // open space, so the character peeks through there. With the 1–5
                    // servers typical users configure, there's always room.
                    Peeker()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(servers, key = { it.id }) { entry ->
                            ServerRow(
                                entry = entry,
                                onClick = { onServerSelected(entry) },
                                onEdit = { onEditServerClicked(entry) },
                                onDelete = { pendingDelete = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { victim ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove \"${victim.name}\"?") },
            text = {
                Text(
                    "This deletes the server's credentials from this device. The server " +
                        "itself is not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    appState.serverStore.remove(victim.id)
                    appState.invalidateService(victim.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(
    entry: ServerEntry,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
        ) {
            Column {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    network = entry.network,
                    urlText = entry.baseUrl,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = {
                    menuOpen = false
                    onEdit()
                })
                DropdownMenuItem(text = { Text("Remove") }, onClick = {
                    menuOpen = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun Row(
    network: BitcoinNetwork,
    urlText: String,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NetworkChip(network)
        Text(
            text = urlText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetworkChip(network: BitcoinNetwork) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(networkColor(network), shape = CircleShape),
        )
        Text(
            text = network.displayName(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyState(onAddServerClicked: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No servers configured",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add your first LDK Server to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            ExtendedFloatingActionButton(
                onClick = onAddServerClicked,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add your first server") },
            )
        }
        Peeker()
    }
}

@Preview
@Composable
private fun ServerListScreenEmptyPreview() {
    LdkServerRemoteTheme {
        ServerListScreen(
            appState =
                TestAppState(
                    serverStore = InMemoryServerStore(),
                    serviceFactory = { FakeLdkService() },
                ),
            onServerSelected = {},
            onAddServerClicked = {},
            onEditServerClicked = {},
        )
    }
}

@Preview
@Composable
private fun ServerListScreenPopulatedPreview() {
    LdkServerRemoteTheme {
        ServerListScreen(
            appState =
                TestAppState(
                    serverStore =
                        InMemoryServerStore(
                            initial =
                                listOf(
                                    ServerEntry(
                                        id = "1",
                                        name = "Home signet",
                                        network = BitcoinNetwork.SIGNET,
                                        baseUrl = "192.168.1.5:3000",
                                        apiKey = "abc",
                                        certificatePem = "",
                                        createdAtEpochSeconds = 0L,
                                    ),
                                    ServerEntry(
                                        id = "2",
                                        name = "Production",
                                        network = BitcoinNetwork.MAINNET,
                                        baseUrl = "node.example.com:443",
                                        apiKey = "def",
                                        certificatePem = "",
                                        createdAtEpochSeconds = 0L,
                                    ),
                                ),
                        ),
                    serviceFactory = { FakeLdkService() },
                ),
            onServerSelected = {},
            onAddServerClicked = {},
            onEditServerClicked = {},
        )
    }
}
