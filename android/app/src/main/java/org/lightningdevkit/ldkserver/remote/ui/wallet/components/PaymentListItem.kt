package org.lightningdevkit.ldkserver.remote.ui.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PaymentKindInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.remote.ui.theme.LdkServerRemoteTheme
import org.lightningdevkit.ldkserver.remote.util.SatsFormatter
import org.lightningdevkit.ldkserver.remote.util.TimeFormatter

@Composable
fun PaymentListItem(
    payment: PaymentInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DirectionBadge(direction = payment.direction)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kindLabel(payment.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.padding(end = 8.dp))
                StatusDot(status = payment.status)
            }
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = TimeFormatter.relativeTime(payment.latestUpdateTimestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text =
                    payment.amountMsat?.let { amountText(payment.direction, it) }
                        ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DirectionBadge(direction: PaymentDirection) {
    val background =
        when (direction) {
            PaymentDirection.INBOUND -> MaterialTheme.colorScheme.tertiaryContainer
            PaymentDirection.OUTBOUND -> MaterialTheme.colorScheme.secondaryContainer
        }
    val icon =
        when (direction) {
            PaymentDirection.INBOUND -> Icons.Filled.ArrowDownward
            PaymentDirection.OUTBOUND -> Icons.Filled.ArrowUpward
        }
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription =
                if (direction == PaymentDirection.INBOUND) "Received" else "Sent",
        )
    }
}

@Composable
private fun StatusDot(status: PaymentStatus) {
    val color =
        when (status) {
            PaymentStatus.SUCCEEDED -> Color(0xFF2FA44F) // green
            PaymentStatus.PENDING -> Color(0xFFE2B007) // amber
            PaymentStatus.FAILED -> MaterialTheme.colorScheme.error
        }
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
    )
}

private fun kindLabel(kind: PaymentKindInfo?): String =
    when (kind) {
        is PaymentKindInfo.Onchain -> "On-chain"
        is PaymentKindInfo.Bolt11 -> "Lightning"
        is PaymentKindInfo.Bolt11Jit -> "Lightning (JIT)"
        is PaymentKindInfo.Bolt12Offer -> "BOLT12"
        is PaymentKindInfo.Bolt12Refund -> "BOLT12 refund"
        is PaymentKindInfo.Spontaneous -> "Spontaneous"
        null -> "Payment"
    }

private fun amountText(
    direction: PaymentDirection,
    amountMsat: ULong,
): String {
    val sign =
        when (direction) {
            PaymentDirection.INBOUND -> "+"
            PaymentDirection.OUTBOUND -> "−"
        }
    return "$sign ${SatsFormatter.formatMsatsAsSats(amountMsat)}"
}

@Preview
@Composable
private fun PaymentListItemPreview() {
    LdkServerRemoteTheme {
        Column {
            PaymentListItem(
                payment =
                    PaymentInfo(
                        id = "1",
                        kind = PaymentKindInfo.Bolt11(hash = "h", preimage = "p"),
                        amountMsat = 50_000_000UL,
                        feePaidMsat = 1_234UL,
                        direction = PaymentDirection.OUTBOUND,
                        status = PaymentStatus.SUCCEEDED,
                        latestUpdateTimestamp = (System.currentTimeMillis() / 1000 - 120).toULong(),
                    ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PaymentListItem(
                payment =
                    PaymentInfo(
                        id = "2",
                        kind = PaymentKindInfo.Onchain(txid = "abc"),
                        amountMsat = 1_000_000_000UL,
                        feePaidMsat = null,
                        direction = PaymentDirection.INBOUND,
                        status = PaymentStatus.PENDING,
                        latestUpdateTimestamp = (System.currentTimeMillis() / 1000 - 7200).toULong(),
                    ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
