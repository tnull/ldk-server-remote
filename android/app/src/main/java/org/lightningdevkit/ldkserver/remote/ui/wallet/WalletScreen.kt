package org.lightningdevkit.ldkserver.remote.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.ui.AppState

/** Placeholder — fleshed out in Step 12 with balance, action buttons, and payments. */
@Composable
fun WalletScreen(
    appState: AppState,
    serverId: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Wallet tab",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "(balance + send/receive + activity coming in Step 12)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
