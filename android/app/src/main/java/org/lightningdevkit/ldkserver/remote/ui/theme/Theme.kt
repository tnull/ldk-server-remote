package org.lightningdevkit.ldkserver.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork

@Composable
fun LdkServerRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}

/** Color used for the network chip on a server-list row. */
fun networkColor(network: BitcoinNetwork): Color =
    when (network) {
        BitcoinNetwork.MAINNET -> NetworkMainnetColor
        BitcoinNetwork.TESTNET -> NetworkTestnetColor
        BitcoinNetwork.SIGNET -> NetworkSignetColor
        BitcoinNetwork.REGTEST -> NetworkRegtestColor
    }
