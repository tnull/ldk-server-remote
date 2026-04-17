package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {
    @Test
    fun short_strings_pass_through_unchanged() {
        assertEquals("", "".truncateMiddle())
        assertEquals("abc", "abc".truncateMiddle(keep = 8))
        // keep=8 means threshold is 2*8+1 = 17 chars; 16 should not be truncated
        assertEquals("abcdefghijklmnop", "abcdefghijklmnop".truncateMiddle(keep = 8))
    }

    @Test
    fun long_strings_get_middle_truncated() {
        val nodeId = "03abf30d19c8f85c1f2af9b27b0a7a00f0a24b1a9c3d4e5f607182930a1b2c3d4e"
        assertEquals("03abf30d…1b2c3d4e", nodeId.truncateMiddle(keep = 8))
    }

    @Test
    fun keep_controls_both_ends_equally() {
        val s = "0123456789abcdef0123456789abcdef"
        assertEquals("0123…cdef", s.truncateMiddle(keep = 4))
        assertEquals("012345…abcdef", s.truncateMiddle(keep = 6))
    }
}
