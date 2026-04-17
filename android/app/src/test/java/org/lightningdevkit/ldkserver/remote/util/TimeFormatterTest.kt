package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatterTest {
    private val now = 1_700_000_000L // fixed anchor so tests are deterministic

    @Test
    fun just_now_for_sub_minute() {
        assertEquals("just now", TimeFormatter.relativeTime(now.toULong(), now))
        assertEquals("just now", TimeFormatter.relativeTime((now - 59).toULong(), now))
    }

    @Test
    fun minutes_then_hours_then_days() {
        assertEquals("2 min ago", TimeFormatter.relativeTime((now - 125).toULong(), now))
        assertEquals("1 hr ago", TimeFormatter.relativeTime((now - 3_700).toULong(), now))
        assertEquals("3 d ago", TimeFormatter.relativeTime((now - 3 * 86_400).toULong(), now))
    }

    @Test
    fun future_timestamps_use_in_prefix() {
        // Clock skew between client and server shouldn't render as a nonsense negative.
        assertEquals("in 5 min", TimeFormatter.relativeTime((now + 300).toULong(), now))
    }

    @Test
    fun far_past_falls_back_to_short_date() {
        // 30 days ago — past the "d ago" threshold, should show MMM d (or with year).
        val rendered = TimeFormatter.relativeTime((now - 30L * 86_400).toULong(), now)
        assertTrue("expected a date-like fallback, got: $rendered", rendered.matches(Regex("[A-Z][a-z]{2} \\d{1,2}(, \\d{4})?")))
    }
}
