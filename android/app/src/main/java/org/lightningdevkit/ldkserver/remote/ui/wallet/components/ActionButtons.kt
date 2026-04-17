package org.lightningdevkit.ldkserver.remote.ui.wallet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme

/**
 * Send / Receive action row. Two equally-sized buttons sitting right under the
 * balance card — per the Bitcoin Design Guide, these are the app's most-used
 * actions and need to be reachable without scrolling.
 */
@Composable
fun ActionButtons(
    onSendClicked: () -> Unit,
    onReceiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onSendClicked,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null)
            Spacer(Modifier.padding(end = 6.dp))
            Text("Send", fontWeight = FontWeight.SemiBold)
        }
        FilledTonalButton(
            onClick = onReceiveClicked,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null)
            Spacer(Modifier.padding(end = 6.dp))
            Text("Receive", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview
@Composable
private fun ActionButtonsPreview() {
    LdkServerRemoteTheme {
        ActionButtons(
            onSendClicked = {},
            onReceiveClicked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
