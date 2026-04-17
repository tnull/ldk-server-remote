package org.lightningdevkit.ldkserver.remote.ui.channels

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

/** Placeholder — fleshed out in Step 15 with channel list + open/close actions. */
@Composable
fun ChannelsScreen(
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
            text = "Channels tab",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "(list + open/close coming in Step 15)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
