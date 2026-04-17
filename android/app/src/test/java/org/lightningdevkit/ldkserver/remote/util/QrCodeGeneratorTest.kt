package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QrCodeGenerator uses android.graphics.Bitmap, so it needs Robolectric's Android
 * stubs to run on the JVM. We cover encoding validity + size sanity; we don't try
 * to decode the matrix (we'd be testing ZXing itself then).
 */
@RunWith(RobolectricTestRunner::class)
class QrCodeGeneratorTest {
    @Test
    fun encodes_a_short_string_into_a_non_null_bitmap() {
        val bmp = QrCodeGenerator.encode("hello", sizePx = 200)
        assertNotNull(bmp)
    }

    @Test
    fun encodes_a_bolt11_looking_string() {
        val invoice = "lnbc1p00fakeinvoicexxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        val bmp = QrCodeGenerator.encode(invoice, sizePx = 400)
        assertNotNull(bmp)
    }

    @Test
    fun returns_null_for_empty_content() {
        // ZXing throws IllegalArgumentException for empty input; we swallow and return null.
        assertNull(QrCodeGenerator.encode("", sizePx = 100))
    }
}
