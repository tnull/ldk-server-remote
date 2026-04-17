package org.lightningdevkit.ldkserver.remote.ui.receive

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * While this composable is in the tree, boost the window's screen brightness to the
 * maximum (1.0). When it leaves the composition we restore the previous value.
 *
 * The Bitcoin Design Guide calls for this on receive-side QR codes: dim screens
 * produce low-contrast QR images, which cheap cameras struggle to read in varied
 * ambient light. It's especially visible at arm's length across a café table.
 */
@Composable
fun KeepScreenBright() {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return
    DisposableEffect(window) {
        val attrs = window.attributes
        val original = attrs.screenBrightness
        window.attributes = attrs.apply { screenBrightness = 1.0f }
        onDispose {
            window.attributes =
                window.attributes.apply {
                    screenBrightness =
                        original.takeIf { it >= 0 }
                            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
        }
    }
}
