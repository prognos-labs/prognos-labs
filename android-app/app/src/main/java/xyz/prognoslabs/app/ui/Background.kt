package xyz.prognoslabs.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import xyz.prognoslabs.app.R

/**
 * Background design, v8. Built from the MEASURED approved preview
 * (see PREVIEW_DIFF.md), not from memory.
 *
 * The preview's background is three layers, all visible on a real phone:
 * 1. A lime glow: a 340dp circle, top:-130 right:-110, radial lime 0.65
 *    fading to 0 at 68%.
 * 2. A SOLID ribbon-P image, 380dp, top:-120 right:-165, opacity 0.07 light
 *    / 0.12 dark in the preview. v7 drew the motif as a thin STROKE outline
 *    on top of the brightest part of the glow, so it washed out completely
 *    on Flint's phone. v8 uses the solid motif PNG (res/drawable/motif_light
 *    and motif_dark, rendered from the real logo v3 geometry) at 0.15 light /
 *    0.18 dark, which the HTML mock render proved reads clearly in both
 *    themes. The alphas are named constants so a unit test can fail the
 *    build if they ever drift back below visibility.
 * 3. A dot field, bottom left: 170x120dp, 1.6dp dots on a 15dp grid,
 *    alpha 0.20 light / 0.22 dark.
 *
 * Everything is static vector/image work in one Canvas plus one Image per
 * screen: no bitmap backgrounds, no blur, no animation, no per-frame work.
 * The only bitmaps in the app are the 16 KB logo and the motif PNGs.
 *
 * v8 DELETED the dashboard header band (DashboardHeaderBand): the approved
 * preview has NO dark band on the dashboard, its header is plain, with a
 * greeting, an avatar circle and bordered icon buttons.
 */
object BackgroundDesign {
    const val GlowAlphaLight = 0.65f
    const val GlowAlphaDark = 0.65f
    const val MotifAlphaLight = 0.15f
    const val MotifAlphaDark = 0.18f
    const val DotAlpha = 0.22f
    const val WalletMotifAlpha = 0.10f
}

/** The real logo image, light or dark variant by theme. Never drawn in code. */
@Composable
fun LogoImage(sizeDp: Int) {
    val c = appColors()
    Image(
        painter = painterResource(
            id = if (c.isDark) R.drawable.prognos_logo_dark else R.drawable.prognos_logo
        ),
        contentDescription = "Prognos logo",
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(9.dp))
    )
}

/**
 * The solid ribbon-P motif image, as the preview draws it: a solid
 * silhouette, not a stroke outline. Black on light, white on dark.
 */
@Composable
fun MotifImage(sizeDp: Int, alpha: Float) {
    val c = appColors()
    Image(
        painter = painterResource(
            id = if (c.isDark) R.drawable.motif_dark else R.drawable.motif_light
        ),
        contentDescription = null,
        modifier = Modifier
            .size(sizeDp.dp)
            .alpha(alpha)
    )
}

/** Full-screen designed background for the splash and auth screens. */
@Composable
fun AuthBackground() {
    val c = appColors()
    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Lime glow, top right, exactly as the preview: a 340dp circle
            // whose centre sits at (0.85w, 0.045h), peak 0.65.
            val glowRadius = w * 0.44f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Lime.copy(alpha = if (c.isDark) BackgroundDesign.GlowAlphaDark else BackgroundDesign.GlowAlphaLight),
                        Lime.copy(alpha = 0.0f)
                    ),
                    center = Offset(w * 0.85f, h * 0.045f),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(w * 0.85f, h * 0.045f)
            )
            // Dot field, bottom left corner only, so the form stays
            // readable: 170x120dp, 1.6dp dots on a 15dp grid.
            val dotColor = c.ink.copy(alpha = BackgroundDesign.DotAlpha)
            val step = 15.dp.toPx()
            var row = 0
            var y = h - 120.dp.toPx() + step / 2f
            while (y < h && row < 8) {
                var x = step / 2f + if (row % 2 == 0) 0f else step / 2f
                var col = 0
                while (x < 170.dp.toPx() && col < 12) {
                    drawCircle(dotColor, radius = 1.6.dp.toPx(), center = Offset(x, y))
                    x += step
                    col++
                }
                y += step
                row++
            }
        }
        // Giant ribbon-P motif, cropped off the top right edge, exactly as
        // the preview positions it: 380dp, top -120dp, right -165dp.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 165.dp, y = (-120).dp)
        ) {
            MotifImage(
                sizeDp = 380,
                alpha = if (c.isDark) BackgroundDesign.MotifAlphaDark else BackgroundDesign.MotifAlphaLight
            )
        }
    }
}

/**
 * Wallet background: the solid ribbon-P motif at the bottom right,
 * exactly as the approved preview draws it: a 230dp image at right:-70,
 * bottom:60, opacity 0.05 in the preview. v8 uses 0.10 so it is clearly
 * visible on a real phone (the preview's 0.05 already reads, but the
 * unit test floor is 0.10). Static image, drawn once. v7 placed a
 * 380dp stroked outline at bottom:-120 right:-165, which showed almost
 * nothing: the visible window of that placement contains only a sliver
 * of the P, which is why the v6 video's wallet was plain white.
 */
@Composable
fun WalletBackground(content: @Composable () -> Unit) {
    val c = appColors()
    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = (-60).dp)
        ) {
            MotifImage(sizeDp = 230, alpha = BackgroundDesign.WalletMotifAlpha)
        }
        content()
    }
}
