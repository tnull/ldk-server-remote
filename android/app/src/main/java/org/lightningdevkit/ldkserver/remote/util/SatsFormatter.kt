package org.lightningdevkit.ldkserver.remote.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Centralised Bitcoin-amount formatting.
 *
 * Follows the Bitcoin Design Guide's "₿-only" convention
 * (https://bitcoin.design/guide/designing-products/units-and-symbols/#-only-format):
 *
 *  * Always show integer quantities — no decimals in product UIs.
 *  * Prefix the quantity with `₿` (no space, matching the guide's examples
 *    `₿5,449`, `₿15,000`).
 *  * Don't print the words `sats` / `satoshis` alongside a rendered value.
 *
 * There's one intentional exception to the last rule: **text-input labels keep
 * `(sats)`**. In a form field an `₿`-suffixed label is easy to misread as
 * `(BTC)` and then the user types 1 expecting to send 1 BTC. "sats" is
 * unambiguous in that context. Rendered values from this formatter already carry
 * the `₿` prefix, so there's no mixed notation next to an entered amount.
 *
 * Number grouping is always English (`,` thousands separator) regardless of device
 * locale — Bitcoin amounts routinely end up pasted into logs and tickets, and
 * locale-dependent separators make those ambiguous.
 *
 * **Milli-sat precision is deliberately excluded.** The guide only speaks to whole
 * satoshis; mixing `₿` with a fractional value would be misleading. Lightning
 * payment lists floor msat amounts to whole sats; detail screens that genuinely
 * need msat precision should emit the raw msat integer with an explicit `msat`
 * label, outside this formatter.
 */
object SatsFormatter {
    const val UNIT = "₿"

    private val grouping =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ENGLISH).apply { groupingSeparator = ',' })

    private const val MSATS_PER_SAT = 1_000UL

    /** `5449` → `"₿5,449"`. */
    fun formatSats(sats: ULong): String = "$UNIT${grouping.format(sats.toLong())}"

    /**
     * Millisatoshi → whole-sats formatter. Floors the sub-sat portion — list-row
     * amounts render cleanly even when the server returns a non-whole-sat total.
     */
    fun formatMsatsAsSats(msats: ULong): String = formatSats(msats / MSATS_PER_SAT)
}
