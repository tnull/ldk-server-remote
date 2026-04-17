package org.lightningdevkit.ldkserver.remote.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Bitcoin-orange primary — the Bitcoin Design Guide calls for a neutral palette with
// clear accent colors. We use the Bitcoin brand orange only for the primary/accent
// role and lean on Material 3's defaults for everything else.
internal val BitcoinOrange = Color(0xFFF7931A)
internal val BitcoinOrangeDark = Color(0xFFBF6C00)

// Network chip colors. Picked to be distinguishable on both light and dark surfaces.
internal val NetworkMainnetColor = Color(0xFFF7931A) // Bitcoin orange
internal val NetworkTestnetColor = Color(0xFF2FA44F) // green
internal val NetworkSignetColor = Color(0xFFBB6BD9) // purple
internal val NetworkRegtestColor = Color(0xFF6B7280) // neutral grey

val LightScheme =
    lightColorScheme(
        primary = BitcoinOrange,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE3C4),
        onPrimaryContainer = Color(0xFF2A1800),
    )

val DarkScheme =
    darkColorScheme(
        primary = BitcoinOrange,
        onPrimary = Color.Black,
        primaryContainer = BitcoinOrangeDark,
        onPrimaryContainer = Color.White,
    )
