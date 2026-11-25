package xyz.prognoslabs.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.model.ASSET_LABELS
import xyz.prognoslabs.app.model.ASSET_LOGOS
import xyz.prognoslabs.app.model.assetLogoRes

/**
 * v1.1.4: every listed asset shows its real official logo, bundled in
 * the app. No network loading of logos, no AI-made marks, no made-up
 * mark for an asset with no bundled logo (it keeps the letter circle).
 */
class V114Test {

    private val expected = listOf("BTC", "ETH", "XRP", "SOL", "DOGE", "BNB", "HYPE", "ZEC")

    private fun resDir(): File {
        // Unit tests run with the module dir (app/) as the working
        // directory; older runs used the project root. Try both.
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File("src/main/res/drawable"),
            File("$cwd/src/main/res/drawable"),
            File("app/src/main/res/drawable"),
            File("$cwd/app/src/main/res/drawable")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError("Cannot find src/main/res/drawable from $cwd")
    }

    private fun sourceFile(relative: String): File {
        val cwd = System.getProperty("user.dir") ?: "."
        return listOf(
            File(relative),
            File("$cwd/$relative"),
            File("app/$relative"),
            File("$cwd/app/$relative")
        ).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find $relative from $cwd")
    }

    @Test
    fun everyListedAssetHasALogo() {
        for (code in expected) {
            assertNotNull("No logo mapped for $code", ASSET_LOGOS[code])
            assertTrue("Logo res for $code must be a real id", ASSET_LOGOS[code]!! != 0)
        }
        assertEquals("Logo map must cover exactly the listed assets", expected.size, ASSET_LOGOS.size)
    }

    @Test
    fun logoDrawablesAreDistinct() {
        val ids = ASSET_LOGOS.values.toList()
        assertEquals("Each asset needs its own logo drawable", ids.size, ids.toSet().size)
    }

    @Test
    fun logoFilesExistOnDisk() {
        val dir = resDir()
        for (code in expected) {
            val f = File(dir, "ic_asset_" + code.lowercase() + ".png")
            assertTrue("Missing bundled logo file " + f.name, f.isFile)
            assertTrue("Logo file " + f.name + " must not be empty", f.length() > 0)
        }
    }

    @Test
    fun unknownAssetHasNoLogo() {
        assertNull("An unknown asset must not get a made-up logo", assetLogoRes("PEPE"))
        assertNull("Null asset must not get a logo", assetLogoRes(null))
        assertNull("Lowercase code must not match", assetLogoRes("btc"))
    }

    @Test
    fun logoMapMatchesLabelMap() {
        assertEquals(
            "Every asset with a label must have a logo and vice versa",
            ASSET_LABELS.keys.sorted(),
            ASSET_LOGOS.keys.sorted()
        )
    }

    @Test
    fun uiUsesAssetLogoNotLetterCircle() {
        val design = sourceFile("src/main/java/xyz/prognoslabs/app/ui/Design.kt")
        val screens = sourceFile("src/main/java/xyz/prognoslabs/app/ui/Screens.kt")
        val designText = design.readText()
        val screensText = screens.readText()
        // Mover cards, watchlist rows and market rows show the logo.
        assertTrue("MoverCard must use AssetLogo", designText.contains("AssetLogo(a.asset"))
        assertTrue("WatchlistRow and MarketRow must use AssetLogo", designText.contains("AssetLogo(market.asset"))
        assertTrue("Market detail header must use AssetLogo", screensText.contains("AssetLogo(market.asset"))
        // The letter circle stays only as the fallback inside AssetLogo.
        assertTrue(
            "LetterCircle must only be called from AssetLogo as a fallback",
            designText.split("LetterCircle(").size == 3 && screensText.contains("LetterCircle(").not()
        )
    }
}
