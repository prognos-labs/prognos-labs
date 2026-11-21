package xyz.fruitslice.prognos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fruitslice.prognos.ui.BackgroundDesign
import xyz.fruitslice.prognos.ui.PrognosFontFamily
import xyz.fruitslice.prognos.ui.PrognosMonoFamily
import xyz.fruitslice.prognos.ui.PrognosTypography

/**
 * v7/v8 regression tests for the two failures Flint found on his phone:
 * (1) the app rendered in the phone's handwriting font because no font
 * was bundled; (2) the background design was invisible because its
 * alphas were too faint and the motif was a stroke outline on top of
 * the brightest part of the glow. These tests fail the build if the
 * bundled fonts ever go missing from the theme, or if the background
 * alphas ever drift back below visibility.
 *
 * Note: the font files themselves are verified in the built APK with
 * `aapt list` (res/font must contain all 9 TTFs). These tests cover
 * the wiring: the theme's type scale really uses the bundled families,
 * and the background constants really are visible values.
 *
 * v8: the motif is a SOLID image (res/drawable/motif_light and
 * motif_dark, rendered from the real logo v3 geometry), not a stroke
 * outline, and the dashboard header band is deleted: the approved
 * preview has no dark band on the dashboard.
 */
class FontAndBackgroundTest {

    @Test
    fun themeUsesBundledSansFamily() {
        // Every text style in the app's type scale must use the bundled
        // Archivo family, so no Text can fall back to the system font.
        assertEquals(PrognosFontFamily, PrognosTypography.bodyLarge.fontFamily)
        assertEquals(PrognosFontFamily, PrognosTypography.bodyMedium.fontFamily)
        assertEquals(PrognosFontFamily, PrognosTypography.titleLarge.fontFamily)
        assertEquals(PrognosFontFamily, PrognosTypography.labelLarge.fontFamily)
        assertEquals(PrognosFontFamily, PrognosTypography.headlineLarge.fontFamily)
    }

    @Test
    fun fontResourceIdsAreDistinct() {
        // The 9 bundled font files must be distinct resources. (Their
        // presence in the APK is verified with aapt list at build time.)
        val ids = setOf(
            R.font.archivo_regular, R.font.archivo_medium,
            R.font.archivo_semibold, R.font.archivo_bold,
            R.font.archivo_extrabold, R.font.plexmono_regular,
            R.font.plexmono_medium, R.font.plexmono_semibold,
            R.font.plexmono_bold
        )
        assertEquals(9, ids.size)
        for (id in ids) assertNotEquals(0, id)
    }

    @Test
    fun authGlowIsVisible() {
        // v6 peaked at 0.30 light / 0.20 dark and read as plain white.
        assertTrue(BackgroundDesign.GlowAlphaLight >= 0.45f)
        assertTrue(BackgroundDesign.GlowAlphaDark >= 0.35f)
    }

    @Test
    fun authMotifIsVisible() {
        // v6 was 0.05 light / 0.07 dark: invisible. v7 was a stroke
        // outline at 0.18/0.22 on top of the glow: also invisible on a
        // real phone. v8 uses a solid image at 0.15/0.18, which the
        // HTML mock render proved reads clearly in both themes.
        assertTrue(BackgroundDesign.MotifAlphaLight >= 0.15f)
        assertTrue(BackgroundDesign.MotifAlphaDark >= 0.18f)
    }

    @Test
    fun motifDrawablesExist() {
        // The v8 motif is a solid image, not a stroke drawn in code.
        assertNotEquals(0, R.drawable.motif_light)
        assertNotEquals(0, R.drawable.motif_dark)
    }

    @Test
    fun dotFieldIsVisible() {
        // v6 dots were 0.10: invisible.
        assertTrue(BackgroundDesign.DotAlpha >= 0.18f)
    }

    @Test
    fun walletMotifIsVisible() {
        assertTrue(BackgroundDesign.WalletMotifAlpha >= 0.10f)
    }

    @Test
    fun monoFamilyDiffersFromSansFamily() {
        // Prices and labels must use the mono family, not the sans one.
        assertNotEquals(PrognosFontFamily, PrognosMonoFamily)
    }
}
