package org.lightningdevkit.ldkserver.remote.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR encoder used by the Receive screen. Lightweight wrapper around ZXing core. */
object QrCodeGenerator {
    /**
     * Encode [content] as a QR bitmap of [sizePx] × [sizePx] pixels.
     *
     * Returns `null` on encoder failure — ZXing throws when the content is too large
     * for any QR version (extremely unlikely for our payloads but defensive).
     *
     * Per the Bitcoin Design Guide, bech32 payloads are uppercased before encoding:
     * uppercase bech32 fits ZXing's alphanumeric mode (3.5 bits/char), vs byte mode
     * for lowercase (8 bits/char). That's a ~2x density win → smaller, easier-to-scan
     * QR codes. See [uppercaseBech32IfApplicable].
     */
    fun encode(
        content: String,
        sizePx: Int,
    ): ImageBitmap? {
        if (content.isEmpty()) return null
        val text = uppercaseBech32IfApplicable(content)
        return try {
            val hints =
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 1,
                )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            // ZXing throws both WriterException and IllegalArgumentException depending on
            // the input; fold both into a null so the caller can render a fallback.
            null
        }
    }

    /**
     * Uppercase bech32 / bech32m strings so they encode in QR's alphanumeric mode.
     *
     * Detection: all-lowercase ASCII with a known bech32-ish HRP prefix. On-chain
     * legacy (base58) addresses and anything with mixed case are left as-is.
     */
    private fun uppercaseBech32IfApplicable(content: String): String {
        if (content.any { it.isUpperCase() }) return content
        val lower = content.lowercase()
        val prefixes = listOf("lnbc", "lntb", "lnbcrt", "lnbs", "lno", "lnr", "bc1", "tb1", "bcrt1", "tbs1")
        if (prefixes.none { lower.startsWith(it) }) return content
        return content.uppercase()
    }
}
