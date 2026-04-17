package org.lightningdevkit.ldkserver.remote.ui.wallet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.util.SatsFormatter

/**
 * Prominent balance display at the top of the Wallet tab.
 *
 * Follows the Bitcoin Design Guide's "balance prominence" principle: the total
 * spendable balance is the single largest element on the screen, with the
 * on-chain / lightning breakdown in a smaller supporting row underneath.
 */
@Composable
fun BalanceCard(
    totalSats: ULong,
    onchainSats: ULong,
    lightningSats: ULong,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "Balance",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = SatsFormatter.formatSats(totalSats),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BreakdownCell(label = "On-chain", value = SatsFormatter.formatSats(onchainSats))
                BreakdownCell(label = "Lightning", value = SatsFormatter.formatSats(lightningSats))
            }
        }
    }
}

@Composable
private fun BreakdownCell(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview
@Composable
private fun BalanceCardPreview() {
    LdkServerRemoteTheme {
        BalanceCard(
            totalSats = 3_345_678UL,
            onchainSats = 1_000_000UL,
            lightningSats = 2_345_678UL,
            modifier = Modifier.padding(16.dp),
        )
    }
}
