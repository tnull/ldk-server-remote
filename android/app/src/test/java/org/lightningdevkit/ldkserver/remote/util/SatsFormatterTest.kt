package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SatsFormatterTest {
    @Test
    fun zero_sats() {
        assertEquals("0 sats", SatsFormatter.formatSats(0UL))
    }

    @Test
    fun small_and_medium_sats_are_grouped() {
        assertEquals("999 sats", SatsFormatter.formatSats(999UL))
        assertEquals("1,000 sats", SatsFormatter.formatSats(1_000UL))
        assertEquals("1,234,567 sats", SatsFormatter.formatSats(1_234_567UL))
    }

    @Test
    fun sats_grouping_handles_total_bitcoin_supply() {
        // 21M BTC × 100M sats/BTC = 2.1 × 10^15 sats (the entire Bitcoin supply upper bound).
        assertEquals("2,100,000,000,000,000 sats", SatsFormatter.formatSats(2_100_000_000_000_000UL))
    }

    @Test
    fun msats_as_sats_floor_divides() {
        // 999 msat rounds down to 0 sats; 1000 msat = 1 sat.
        assertEquals("0 sats", SatsFormatter.formatMsatsAsSats(999UL))
        assertEquals("1 sats", SatsFormatter.formatMsatsAsSats(1_000UL))
        assertEquals("1 sats", SatsFormatter.formatMsatsAsSats(1_999UL))
        assertEquals("1,234 sats", SatsFormatter.formatMsatsAsSats(1_234_000UL))
    }

    @Test
    fun msats_precise_preserves_leading_fraction_zeros() {
        assertEquals("0.005 sats", SatsFormatter.formatMsatsPrecise(5UL))
        assertEquals("1.234 sats", SatsFormatter.formatMsatsPrecise(1_234UL))
        // When fraction is zero, the decimal point goes away.
        assertEquals("1,000 sats", SatsFormatter.formatMsatsPrecise(1_000_000UL))
    }
}
