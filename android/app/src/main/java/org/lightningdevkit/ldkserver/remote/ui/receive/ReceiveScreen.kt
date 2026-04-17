package org.lightningdevkit.ldkserver.remote.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.ui.common.Peeker
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.util.QrCodeGenerator

/**
 * Full-screen Receive flow over the Wallet tab.
 *
 * Walks the user through picking a payment type, filling in amount / description,
 * and viewing a QR payload they can share. Per the Bitcoin Design Guide, the
 * wallet balance is intentionally NOT shown here (on-looker privacy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    BackHandler {
        when (state.step) {
            ReceiveStep.TypePicker -> onDismiss()
            ReceiveStep.Form -> viewModel.onBackFromForm()
            ReceiveStep.Qr -> viewModel.onBackFromQr()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state.step)) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state.step) {
                            ReceiveStep.TypePicker -> onDismiss()
                            ReceiveStep.Form -> viewModel.onBackFromForm()
                            ReceiveStep.Qr -> viewModel.onBackFromQr()
                        }
                    }) {
                        Icon(
                            imageVector =
                                if (state.step == ReceiveStep.TypePicker) {
                                    Icons.Filled.Close
                                } else {
                                    Icons.Filled.ArrowBack
                                },
                            contentDescription = if (state.step == ReceiveStep.TypePicker) "Close" else "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when (state.step) {
                ReceiveStep.TypePicker -> TypePicker(onPick = viewModel::onTypeChosen)
                ReceiveStep.Form -> FormStep(state = state, viewModel = viewModel)
                ReceiveStep.Qr -> QrStep(state = state, onRegenerate = viewModel::generate)
            }
        }
    }
}

// ---- Type picker -----------------------------------------------------------

@Composable
private fun TypePicker(onPick: (ReceiveType) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Peeker()
        TypePickerCards(onPick)
    }
}

@Composable
private fun TypePickerCards(onPick: (ReceiveType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "How should the sender pay you?",
            style = MaterialTheme.typography.titleMedium,
        )
        TypeOption(
            label = "Lightning (BOLT11 invoice)",
            description = "Fast, private, amount-specific. Works for one-off payments.",
            icon = Icons.Filled.FlashOn,
            onClick = { onPick(ReceiveType.BOLT11) },
        )
        TypeOption(
            label = "Lightning (BOLT12 offer)",
            description = "Reusable offer. The sender sets the amount (if left open).",
            icon = Icons.Filled.FlashOn,
            onClick = { onPick(ReceiveType.BOLT12) },
        )
        TypeOption(
            label = "On-chain",
            description = "Slower and more public, but works with any Bitcoin wallet.",
            icon = Icons.Filled.CurrencyBitcoin,
            onClick = { onPick(ReceiveType.ONCHAIN) },
        )
    }
}

@Composable
private fun TypeOption(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Form step (amount + description) --------------------------------------

@Composable
private fun FormStep(
    state: ReceiveUiState,
    viewModel: ReceiveViewModel,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = state.type?.friendlyName() ?: "",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.amountSatsInput,
            onValueChange = viewModel::onAmountSatsChange,
            label = { Text("Amount (sats)") },
            supportingText = {
                Text(
                    if (state.type == ReceiveType.BOLT11) {
                        "Leave empty for a variable-amount invoice."
                    } else {
                        "Leave empty for a zero-amount offer."
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Description") },
            supportingText = {
                val required = state.type == ReceiveType.BOLT12
                Text(if (required) "Required for BOLT12 offers." else "Optional note embedded in the invoice.")
            },
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.expirySecsInput,
            onValueChange = viewModel::onExpirySecsChange,
            label = { Text("Expires in (seconds)") },
            supportingText = { Text("Default 3600s (1 hour).") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::generate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Generate")
        }
    }
}

// ---- QR step ---------------------------------------------------------------

@Composable
private fun QrStep(
    state: ReceiveUiState,
    onRegenerate: () -> Unit,
) {
    KeepScreenBright()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isGenerating ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Couldn't generate.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRegenerate) { Text("Retry") }
                }
            state.generatedPayload != null -> {
                val payload = state.generatedPayload
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color.White,
                        ),
                ) {
                    val qr = QrCodeGenerator.encode(payload, sizePx = 800)
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "Payment request QR",
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Payload too long to render as a QR code.")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService<ClipboardManager>()
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Payment request", payload))
                            scope.launch { snackbar.showSnackbar("Copied to clipboard") }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.padding(end = 6.dp))
                        Text("Copy")
                    }
                    FilledTonalButton(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                            context.startActivity(Intent.createChooser(send, "Share payment request"))
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.padding(end = 6.dp))
                        Text("Share")
                    }
                }
            }
        }
        SnackbarHost(snackbar) { Snackbar(it) }
    }
}

// ---- Helpers ---------------------------------------------------------------

private fun ReceiveType.friendlyName(): String =
    when (this) {
        ReceiveType.ONCHAIN -> "On-chain address"
        ReceiveType.BOLT11 -> "BOLT11 Lightning invoice"
        ReceiveType.BOLT12 -> "BOLT12 offer"
    }

private fun titleFor(step: ReceiveStep): String =
    when (step) {
        ReceiveStep.TypePicker -> "Receive"
        ReceiveStep.Form -> "Request amount"
        ReceiveStep.Qr -> "Share with sender"
    }

@Preview
@Composable
private fun ReceiveScreenPreview() {
    LdkServerRemoteTheme {
        ReceiveScreen(viewModel = ReceiveViewModel(FakeLdkService()), onDismiss = {})
    }
}
