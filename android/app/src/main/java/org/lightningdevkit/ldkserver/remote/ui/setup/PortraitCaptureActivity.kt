package org.lightningdevkit.ldkserver.remote.ui.setup

import android.widget.ImageButton
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import org.lightningdevkit.ldkserver.remote.R

/**
 * Portrait-locked variant of zxing-android-embedded's [CaptureActivity], with a custom
 * layout that adds an explicit close (X) button. The upstream activity is locked to
 * `sensorLandscape` and its default layout provides no visible way to abort the scan
 * other than the system back gesture — easy to miss while the camera is full-screen.
 *
 * Orientation is enforced via the manifest entry for this class
 * (`android:screenOrientation="sensorPortrait"`).
 */
class PortraitCaptureActivity : CaptureActivity() {
    /**
     * Parent CaptureActivity calls this from its `onCreate` to inflate the layout and
     * return the embedded scanner view. We swap in our own layout here (which must
     * still contain a `DecoratedBarcodeView` with id `@id/zxing_barcode_scanner` for
     * the library to find it) and hook the close button.
     */
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.capture_portrait)
        findViewById<ImageButton>(R.id.portrait_capture_close_button)?.setOnClickListener {
            // Parent CaptureManager returns RESULT_CANCELED on finish; the ScanContract
            // result carries contents == null, which our screen already treats as
            // "user dismissed, do nothing".
            finish()
        }
        // `zxing_barcode_scanner` is defined in the ZXing library's R, not ours
        // (nonTransitiveRClass keeps those namespaces separate in Kotlin).
        return findViewById(com.google.zxing.client.android.R.id.zxing_barcode_scanner)
    }
}
