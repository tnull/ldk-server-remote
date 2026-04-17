package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bitcoin Design Guide's ₿-only format:
 *  - ₿ prefix, no space between symbol and digits (matches the guide's examples).
 *  - Integer quantity, no decimal notation, no "sats" word alongside the number.
 */
class SatsFormatterTest {
    @Test
    fun zero_amount() {
        assertEquals("₿0", SatsFormatter.formatSats(0UL))
    }

    @Test
    fun small_and_medium_amounts_are_grouped() {
        assertEquals("₿999", SatsFormatter.formatSats(999UL))
        assertEquals("₿1,000", SatsFormatter.formatSats(1_000UL))
        assertEquals("₿1,234,567", SatsFormatter.formatSats(1_234_567UL))
    }

    @Test
    fun grouping_handles_total_bitcoin_supply() {
        // 21M BTC × 100M ₿ = 2.1 × 10^15 ₿ (full Bitcoin supply upper bound).
        assertEquals("₿2,100,000,000,000,000", SatsFormatter.formatSats(2_100_000_000_000_000UL))
    }

    @Test
    fun millis_as_whole_units_floor_divide() {
        // 999 msat rounds down to 0 whole ₿; 1000 msat = 1 ₿.
        assertEquals("₿0", SatsFormatter.formatMsatsAsSats(999UL))
        assertEquals("₿1", SatsFormatter.formatMsatsAsSats(1_000UL))
        assertEquals("₿1", SatsFormatter.formatMsatsAsSats(1_999UL))
        assertEquals("₿1,234", SatsFormatter.formatMsatsAsSats(1_234_000UL))
    }
}
