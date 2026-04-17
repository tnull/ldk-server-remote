package org.lightningdevkit.ldkserver.remote.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.lightningdevkit.ldkserver.remote.R
import kotlin.random.Random

/**
 * Whimsical decoration from herecomesbitcoin.org. Renders a random character PNG
 * peeking in from one of the bottom corners, offset off-screen so only a portion is
 * visible — enough to be fun, not enough to compete with the real UI.
 *
 * Usage: drop into any `Box` / `Scaffold` content root for an otherwise-mostly-empty
 * screen:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     EmptyContent()
 *     Peeker()
 * }
 * ```
 *
 * The chosen image is stable for the lifetime of the composition via `remember`, so
 * users don't see it flicker through random options during recomposition.
 */
@Composable
fun BoxScope.Peeker(
    seed: Any = Unit,
    modifier: Modifier = Modifier,
) {
    val (resId, corner) =
        remember(seed) {
            val rand = Random
            PEEKER_RES_IDS[rand.nextInt(PEEKER_RES_IDS.size)] to
                if (rand.nextBoolean()) Corner.BottomStart else Corner.BottomEnd
        }
    val (dx, dy, mirrored, align) =
        when (corner) {
            Corner.BottomStart ->
                PeekerPlacement(dx = -100, dy = 80, mirrored = false, alignment = Alignment.BottomStart)
            Corner.BottomEnd ->
                PeekerPlacement(dx = 100, dy = 80, mirrored = true, alignment = Alignment.BottomEnd)
        }
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier =
            modifier
                .align(align)
                .size(240.dp)
                .offset(x = dx.dp, y = dy.dp)
                .graphicsLayer { if (mirrored) scaleX = -1f }
                .alpha(0.75f),
    )
}

/**
 * Convenience: wrap a composable and get a peeker at the bottom for free. Skips the
 * peeker for screens with lots of content — caller decides whether to wrap.
 */
@Composable
fun WithPeeker(
    modifier: Modifier = Modifier,
    seed: Any = Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        Peeker(seed = seed)
    }
}

private enum class Corner { BottomStart, BottomEnd }

private data class PeekerPlacement(
    val dx: Int,
    val dy: Int,
    val mirrored: Boolean,
    val alignment: Alignment,
)

/**
 * The set of characters we ship. Keep this in sync with the PNGs under
 * `res/drawable-nodpi/peeker_*.png`. Expressed as `@DrawableRes` IDs so Compose can
 * load them via `painterResource`.
 */
@Suppress("unused")
private val PEEKER_RES_IDS: List<Int> =
    listOf(
        R.drawable.peeker_acrobat,
        R.drawable.peeker_astronaut,
        R.drawable.peeker_coder,
        R.drawable.peeker_fairy,
        R.drawable.peeker_fancy,
        R.drawable.peeker_hot_air_balloon,
        R.drawable.peeker_hungry,
        R.drawable.peeker_lightning_network,
        R.drawable.peeker_pubkey,
        R.drawable.peeker_queen,
        R.drawable.peeker_sandwich,
        R.drawable.peeker_satoshi,
        R.drawable.peeker_surfer,
        R.drawable.peeker_sushi,
        R.drawable.peeker_triceratops,
        R.drawable.peeker_ufo,
        R.drawable.peeker_unicorn,
    )

@Suppress("unused")
@DrawableRes
private fun markKept(): Int = R.drawable.peeker_unicorn // ensures R refs aren't stripped
