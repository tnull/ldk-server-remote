package org.lightningdevkit.ldkserver.remote.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Centralised formatting for on-chain sats and Lightning msats.
 *
 * All display values in the wallet UI go through these helpers so number grouping,
 * unit labels, and msat→sat conversion stay consistent across screens. Always
 * renders with a space-grouped English locale (e.g. `1,234,567 sats`) rather than
 * the device locale — cryptocurrency amounts frequently land in copy-pasted logs,
 * invoices, and tickets, and locale-dependent separators make those ambiguous.
 */
object SatsFormatter {
    private val grouping =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ENGLISH).apply { groupingSeparator = ',' })

    /** Number of sats per satoshi-in-millisatoshi (1 sat = 1000 msat). */
    private const val MSATS_PER_SAT = 1_000UL

    /** `123456` → `"123,456 sats"`. */
    fun formatSats(sats: ULong): String = "${grouping.format(sats.toLong())} sats"

    /**
     * Truncating msat → sat formatter (drops the sub-sat portion). Used for amounts in
     * list rows where sub-sat precision would be distracting.
     */
    fun formatMsatsAsSats(msats: ULong): String = formatSats(msats / MSATS_PER_SAT)

    /** Lossless msat formatter: `1234567` → `"1,234.567 sats"`. For detail screens. */
    fun formatMsatsPrecise(msats: ULong): String {
        val whole = msats / MSATS_PER_SAT
        val fraction = msats % MSATS_PER_SAT
        if (fraction == 0UL) return formatSats(whole)
        // %03d preserves leading zeros (e.g. 5 msat → "0.005 sats").
        return "${grouping.format(whole.toLong())}.%03d sats".format(fraction.toLong())
    }
}
