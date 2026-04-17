package org.lightningdevkit.ldkserver.remote.ui.channels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.remote.util.SatsFormatter
import org.lightningdevkit.ldkserver.remote.util.truncateMiddle

@Composable
fun ChannelListItem(
    channel: ChannelInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(channel = channel)
                Spacer(Modifier.padding(end = 10.dp))
                Text(
                    text = channel.counterpartyNodeId.truncateMiddle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = SatsFormatter.formatSats(channel.channelValueSats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            CapacityBar(
                inboundMsat = channel.inboundCapacityMsat,
                outboundMsat = channel.outboundCapacityMsat,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Out ${SatsFormatter.formatMsatsAsSats(channel.outboundCapacityMsat)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "In ${SatsFormatter.formatMsatsAsSats(channel.inboundCapacityMsat)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(channel: ChannelInfo) {
    val color =
        when {
            channel.isUsable -> Color(0xFF2FA44F) // green
            channel.isChannelReady -> Color(0xFFE2B007) // amber — ready but peer not connected
            else -> Color(0xFF9E9E9E) // grey — pending confirmation
        }
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
    )
}

@Composable
private fun CapacityBar(
    inboundMsat: ULong,
    outboundMsat: ULong,
) {
    val total = (inboundMsat + outboundMsat).toFloat().coerceAtLeast(1f)
    val outboundFraction = outboundMsat.toFloat() / total
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(outboundFraction.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .weight((1f - outboundFraction).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    ),
        )
    }
}
