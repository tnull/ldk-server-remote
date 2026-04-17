package org.lightningdevkit.ldkserver.remote.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Human-friendly timestamp rendering for payment rows and sync-status indicators.
 *
 * Everything coming from LDK Server is a Unix epoch in seconds. For recent times we
 * render a relative form ("2 min ago"); past a couple of days we fall back to a
 * short date. Tuned for glance-readability — not for calendar-style precision.
 */
object TimeFormatter {
    private const val ONE_MINUTE = 60L
    private const val ONE_HOUR = 60L * ONE_MINUTE
    private const val ONE_DAY = 24L * ONE_HOUR

    private val shortDate = SimpleDateFormat("MMM d", Locale.getDefault())
    private val shortDateWithYear = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    /**
     * Render [epochSecs] relative to [nowSecs]. Grades:
     * ```
     * < 1 min:  "just now"
     * < 1 hr:   "<n> min ago"
     * < 1 day:  "<n> hr ago"
     * < 7 d:    "<n> d ago"
     * same yr:  "Mar 5"
     * older:    "Mar 5, 2023"
     * ```
     * Future timestamps are rendered symmetrically (e.g. "in 5 min"), which handles
     * the small clock-skew case without looking broken.
     */
    fun relativeTime(
        epochSecs: ULong,
        nowSecs: Long = System.currentTimeMillis() / 1000,
    ): String {
        val target = epochSecs.toLong()
        val delta = nowSecs - target
        val future = delta < 0
        val d = abs(delta)

        return when {
            d < ONE_MINUTE -> "just now"
            d < ONE_HOUR -> directional(future, "${d / ONE_MINUTE} min")
            d < ONE_DAY -> directional(future, "${d / ONE_HOUR} hr")
            d < 7 * ONE_DAY -> directional(future, "${d / ONE_DAY} d")
            sameYear(target, nowSecs) -> shortDate.format(Date(target * 1000))
            else -> shortDateWithYear.format(Date(target * 1000))
        }
    }

    private fun directional(
        future: Boolean,
        span: String,
    ): String = if (future) "in $span" else "$span ago"

    private fun sameYear(
        a: Long,
        b: Long,
    ): Boolean {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = a * 1000
        val ya = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = b * 1000
        return ya == cal.get(java.util.Calendar.YEAR)
    }
}
