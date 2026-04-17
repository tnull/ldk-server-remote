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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.remote.util.SatsFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channel: ChannelInfo,
    viewModel: ChannelsViewModel,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var closeDialog by remember { mutableStateOf<CloseDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channel") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            DetailRow("Counterparty", channel.counterpartyNodeId)
            DetailRow("Channel ID", channel.channelId)
            DetailRow("User channel ID", channel.userChannelId)
            channel.fundingTxo?.let { DetailRow("Funding output", "${it.txid}:${it.vout}") }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            DetailRow("Capacity", SatsFormatter.formatSats(channel.channelValueSats))
            DetailRow("Outbound", SatsFormatter.formatMsatsAsSats(channel.outboundCapacityMsat))
            DetailRow("Inbound", SatsFormatter.formatMsatsAsSats(channel.inboundCapacityMsat))
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            DetailRow(
                "Status",
                when {
                    channel.isUsable -> "Usable"
                    channel.isChannelReady -> "Ready (peer not connected)"
                    else -> "Pending confirmation"
                },
            )
            DetailRow("Outbound", if (channel.isOutbound) "Initiated by us" else "Initiated by counterparty")
            DetailRow("Announced", if (channel.isAnnounced) "Yes (public)" else "No (private)")
            channel.confirmations?.let { confs ->
                val req = channel.confirmationsRequired
                DetailRow("Confirmations", if (req != null) "$confs / $req" else confs.toString())
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Close",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cooperative close tries to settle with the counterparty. Force close broadcasts " +
                    "our latest commitment transaction on-chain — use only if the peer is offline " +
                    "or unresponsive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { closeDialog = CloseDialog.Cooperative },
                    modifier = Modifier.weight(1f),
                ) { Text("Close") }
                OutlinedButton(
                    onClick = { closeDialog = CloseDialog.Force },
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) { Text("Force close") }
            }
        }
    }

    closeDialog?.let { kind ->
        var reason by remember(kind) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { closeDialog = null },
            title = { Text(if (kind == CloseDialog.Force) "Force close channel?" else "Close channel?") },
            text = {
                Column {
                    Text(
                        if (kind == CloseDialog.Force) {
                            "This broadcasts your latest commitment on-chain. Funds are subject to the " +
                                "force-close spend delay before you can sweep them."
                        } else {
                            "This asks the peer to sign a mutual close. The peer must be reachable."
                        },
                    )
                    if (kind == CloseDialog.Force) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val force = kind == CloseDialog.Force
                    closeDialog = null
                    viewModel.closeChannel(
                        channel = channel,
                        force = force,
                        reason = reason.ifBlank { null }.takeIf { force },
                    ) { outcome ->
                        outcome.fold(
                            onSuccess = { onDismiss() },
                            onFailure = { e ->
                                scope.launch {
                                    snackbar.showSnackbar(e.message ?: "Failed to close channel")
                                }
                            },
                        )
                    }
                }) { Text(if (kind == CloseDialog.Force) "Force close" else "Close") }
            },
            dismissButton = {
                TextButton(onClick = { closeDialog = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private enum class CloseDialog { Cooperative, Force }
