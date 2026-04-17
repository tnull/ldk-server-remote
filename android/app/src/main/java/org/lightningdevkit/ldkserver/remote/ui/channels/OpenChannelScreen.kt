package org.lightningdevkit.ldkserver.remote.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modal form for opening a new channel. All fields required except the "announce"
 * toggle. On success we dismiss; on failure we surface the server-side message as a
 * snackbar and leave the form populated so the user can retry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenChannelScreen(
    viewModel: ChannelsViewModel,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var nodePubkey by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var amountSats by remember { mutableStateOf("") }
    var announce by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open channel") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = nodePubkey,
                onValueChange = { nodePubkey = it.trim() },
                label = { Text("Counterparty node pubkey (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("Address (host:port)") },
                supportingText = { Text("IPv4:port, IPv6:port, or hostname:port.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountSats,
                onValueChange = { amountSats = it.filter { ch -> ch.isDigit() } },
                label = { Text("Channel amount (sats)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Announce channel", fontWeight = FontWeight.Medium)
                    Text(
                        text = "Make this channel public in the network graph.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = announce, onCheckedChange = { announce = it })
            }

            validationError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val amount = amountSats.toULongOrNull()
                    validationError =
                        when {
                            nodePubkey.isBlank() -> "Counterparty pubkey is required."
                            address.isBlank() -> "Peer address is required."
                            amount == null || amount == 0UL -> "Channel amount must be a positive number of sats."
                            else -> null
                        }
                    if (validationError != null || amount == null) return@Button
                    isSubmitting = true
                    viewModel.openChannel(
                        nodePubkey = nodePubkey,
                        address = address,
                        amountSats = amount,
                        announceChannel = announce,
                    ) { outcome ->
                        isSubmitting = false
                        outcome.fold(
                            onSuccess = { onDismiss() },
                            onFailure = { e ->
                                scope.launch {
                                    snackbar.showSnackbar(e.message ?: "Failed to open channel")
                                }
                            },
                        )
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Opening…")
                } else {
                    Text("Open channel")
                }
            }
        }
    }
}
