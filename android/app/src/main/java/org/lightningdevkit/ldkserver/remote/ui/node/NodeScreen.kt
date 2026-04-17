package org.lightningdevkit.ldkserver.remote.ui.node

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

/** Placeholder — fleshed out in Step 16 with node info, connection string, peers. */
@Composable
fun NodeScreen(
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
            text = "Node tab",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "(info / peers / sync status coming in Step 16)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
