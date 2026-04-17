package org.lightningdevkit.ldkserver.remote.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork
import org.lightningdevkit.ldkserver.remote.model.InMemoryServerStore
import org.lightningdevkit.ldkserver.remote.ui.AppState
import org.lightningdevkit.ldkserver.remote.ui.TestAppState
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.util.ServerUri

/**
 * Add / edit server screen. Renders a vertical form plus a prominent "Scan QR" action
 * that pre-fills the fields from a `ldk-server-remote:` URI.
 *
 * Scanning pre-fills but does NOT auto-save — the user can tweak IP, name, etc. before
 * hitting Save, per the requirement that scanned details be editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditServerScreen(
    appState: AppState,
    editId: String?,
    onDone: () -> Unit,
) {
    val viewModel =
        remember(editId) {
            AddOrEditServerViewModel(store = appState.serverStore, editId = editId)
        }
    AddOrEditServerScreenContent(
        viewModel = viewModel,
        editing = editId != null,
        onDone = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditServerScreenContent(
    viewModel: AddOrEditServerViewModel,
    editing: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state = viewModel.state

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result?.contents ?: return@rememberLauncherForActivityResult
            when (val parsed = ServerUri.decode(contents)) {
                is ServerUri.ParseResult.Success -> viewModel.applyScanned(parsed.fields)
                is ServerUri.ParseResult.Failure -> viewModel.onScanError(parsed.reason)
            }
        }

    val launchScanner: () -> Unit = {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan an LDK Server Remote setup QR")
                setBeepEnabled(false)
                setOrientationLocked(false)
            },
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchScanner()
            } else {
                scope.launch { snackbar.showSnackbar("Camera permission is required to scan a QR code.") }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit server" else "Add server") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            OutlinedButton(
                onClick = {
                    val hasPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        launchScanner()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width16())
                Text("Scan setup QR")
            }

            state.scanInfo?.let { info ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                supportingText = { Text("A label shown in the server list.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            NetworkDropdown(
                current = state.network,
                onSelected = viewModel::onNetworkChange,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("Server URL (host:port)") },
                singleLine = true,
                supportingText = { Text("Do not include http:// or https://.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("API key (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.certificatePem,
                onValueChange = viewModel::onCertificatePemChange,
                label = { Text("TLS certificate (PEM)") },
                supportingText = {
                    Text("Paste the full contents of the server's tls.crt.")
                },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            state.validationError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (viewModel.save()) onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing) "Save changes" else "Add server")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDropdown(
    current: BitcoinNetwork,
    onSelected: (BitcoinNetwork) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Network") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BitcoinNetwork.entries.forEach { net ->
                DropdownMenuItem(
                    text = { Text(net.displayName()) },
                    onClick = {
                        onSelected(net)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Small helper: the standard Compose Spacer API uses dp sizes. This keeps the screen
// code terse at the small cost of a one-line extension.
private fun Modifier.width16(): Modifier = this.then(Modifier.padding(start = 8.dp))

@Preview
@Composable
private fun AddOrEditServerScreenPreview() {
    LdkServerRemoteTheme {
        AddOrEditServerScreen(
            appState =
                TestAppState(
                    serverStore = InMemoryServerStore(),
                    serviceFactory = { throw UnsupportedOperationException() },
                ),
            editId = null,
            onDone = {},
        )
    }
}
