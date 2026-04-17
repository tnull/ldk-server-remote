package org.lightningdevkit.ldkserver.remote.ui.send

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.lightningdevkit.ldkserver.client.UnifiedSendResult
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.ui.setup.PortraitCaptureActivity
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.util.SatsFormatter

/**
 * Full-screen Send flow shown as a modal over the Wallet tab.
 *
 * Owns an internal VM and walks the user through Input → Confirm → Result. System
 * back (gesture or button) either moves one step back, or — on the Input step —
 * dismisses the whole flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: SendViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    BackHandler {
        when (state.step) {
            SendStep.Input -> onDismiss()
            SendStep.Confirm -> viewModel.onBackToInput()
            SendStep.Result -> {
                viewModel.onDismissResult()
                onDismiss()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state.step)) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state.step) {
                            SendStep.Input -> onDismiss()
                            SendStep.Confirm -> viewModel.onBackToInput()
                            SendStep.Result -> {
                                viewModel.onDismissResult()
                                onDismiss()
                            }
                        }
                    }) {
                        Icon(
                            imageVector =
                                if (state.step == SendStep.Input) Icons.Filled.Close else Icons.Filled.ArrowBack,
                            contentDescription = if (state.step == SendStep.Input) "Close" else "Back",
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
                SendStep.Input ->
                    InputStep(state = state, viewModel = viewModel)
                SendStep.Confirm ->
                    ConfirmStep(state = state, viewModel = viewModel)
                SendStep.Result ->
                    ResultStep(
                        state = state,
                        onDone = {
                            viewModel.onDismissResult()
                            onDismiss()
                        },
                        onTryAgain = { viewModel.onBackToInput() },
                    )
            }
        }
    }
}

// ---- Input step ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputStep(
    state: SendUiState,
    viewModel: SendViewModel,
) {
    val context = LocalContext.current

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result?.contents ?: return@rememberLauncherForActivityResult
            viewModel.applyScanned(contents)
        }
    val launchScanner: () -> Unit = {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan a Lightning invoice, BIP21 URI, or on-chain address")
                setBeepEnabled(false)
                setCaptureActivity(PortraitCaptureActivity::class.java)
                setOrientationLocked(true)
            },
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
        }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedButton(
            onClick = {
                val hasPerm =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) launchScanner() else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.padding(end = 6.dp))
            Text("Scan QR")
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            label = { Text("Invoice, offer, address, or BIP21 URI") },
            supportingText = { Text("Paste or scan what you want to pay.") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.amountSatsInput,
            onValueChange = viewModel::onAmountSatsChange,
            label = { Text("Amount (sats)") },
            supportingText = {
                Text("Only needed for variable-amount invoices and zero-amount offers.")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.validationError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::onNextClick,
            enabled = state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

// ---- Confirm step ----------------------------------------------------------

@Composable
private fun ConfirmStep(
    state: SendUiState,
    viewModel: SendViewModel,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("You are about to send:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val invoiceAmount = state.decodedInvoice?.amountMsat
        val offerAmount = state.decodedOffer?.amountMsat
        val effectiveAmountMsat =
            state.amountSats?.let { it * 1_000UL }
                ?: invoiceAmount
                ?: offerAmount

        SummaryRow(
            label = "Amount",
            value =
                effectiveAmountMsat?.let { SatsFormatter.formatMsatsAsSats(it) }
                    ?: "whatever amount the URI encodes",
        )
        state.decodedInvoice?.description?.let { SummaryRow(label = "Description", value = it) }
        state.decodedOffer?.description?.let { SummaryRow(label = "Description", value = it) }
        state.decodedInvoice?.destination?.let { SummaryRow(label = "Destination", value = it.take(20) + "…") }
        SummaryRow(
            label = "Destination (raw)",
            value = state.input.let { if (it.length > 60) it.take(60) + "…" else it },
        )

        state.decodeHint?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::onSendClick,
            enabled = !state.isSending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).height(18.dp),
                    strokeWidth = 2.dp,
                )
                Text("Sending…")
            } else {
                Text("Confirm and send")
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---- Result step -----------------------------------------------------------

@Composable
private fun ResultStep(
    state: SendUiState,
    onDone: () -> Unit,
    onTryAgain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val error = state.sendError
        if (error != null) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(64.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Payment failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        } else {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(64.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Payment sent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(resultSubtitle(state.result), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

private fun titleFor(step: SendStep): String =
    when (step) {
        SendStep.Input -> "Send"
        SendStep.Confirm -> "Confirm send"
        SendStep.Result -> "Send"
    }

private fun resultSubtitle(result: UnifiedSendResult?): String =
    when (result) {
        is UnifiedSendResult.Onchain -> "On-chain tx: ${result.txid.take(12)}…"
        is UnifiedSendResult.Bolt11 -> "Lightning payment id: ${result.paymentId.take(12)}…"
        is UnifiedSendResult.Bolt12 -> "BOLT12 payment id: ${result.paymentId.take(12)}…"
        null -> ""
    }

// ---- Preview ---------------------------------------------------------------

@Preview
@Composable
private fun SendScreenInputPreview() {
    LdkServerRemoteTheme {
        SendScreen(viewModel = SendViewModel(FakeLdkService()), onDismiss = {})
    }
}
