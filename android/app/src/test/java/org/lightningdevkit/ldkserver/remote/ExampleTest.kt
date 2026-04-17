package org.lightningdevkit.ldkserver.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test to prove the JVM test pipeline is wired up. Real VM tests land in the
 * per-feature commits that follow.
 */
class ExampleTest {
    @Test
    fun arithmetic_stillWorks() {
        assertEquals(4, 2 + 2)
    }
}
