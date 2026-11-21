package xyz.fruitslice.prognos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import xyz.fruitslice.prognos.R

val Lime = Color(0xFFC8F542)
val BidGreen = Color(0xFF1E7D32)
val AskRed = Color(0xFFC62828)
val StarGold = Color(0xFFB08A00)

/**
 * The app's own typefaces, bundled in res/font (Archivo + IBM Plex Mono,
 * OFL licensed, static instances per weight). v6 did not bundle a font,
 * so the app inherited whatever font the phone had set: on Flint's phone
 * that was a handwriting font, and the whole app rendered in it. Every
 * text element in the app uses one of these two families, so the app
 * looks identical on any phone. Nothing here falls back to the system
 * font: do not use the platform default or monospace families anywhere
 * in shipped code; use PrognosFontFamily / PrognosMonoFamily.
 */
val PrognosFontFamily = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold)
)

/** Mono family for prices, labels and countdowns. Mono digits are equal
 *  width, so a changing price never shifts the layout. */
val PrognosMonoFamily = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
    Font(R.font.plexmono_semibold, FontWeight.SemiBold),
    Font(R.font.plexmono_bold, FontWeight.Bold)
)

/** One type scale, all in the bundled Archivo family. MaterialTheme's
 *  default font family is set here, so any Text that does not name a
 *  family still renders in Archivo. Internal so the unit tests can
 *  assert the theme really uses the bundled family. */
internal val PrognosTypography = Typography(
    displayLarge = TextStyle(fontFamily = PrognosFontFamily),
    displayMedium = TextStyle(fontFamily = PrognosFontFamily),
    displaySmall = TextStyle(fontFamily = PrognosFontFamily),
    headlineLarge = TextStyle(fontFamily = PrognosFontFamily),
    headlineMedium = TextStyle(fontFamily = PrognosFontFamily),
    headlineSmall = TextStyle(fontFamily = PrognosFontFamily),
    titleLarge = TextStyle(fontFamily = PrognosFontFamily),
    titleMedium = TextStyle(fontFamily = PrognosFontFamily),
    titleSmall = TextStyle(fontFamily = PrognosFontFamily),
    bodyLarge = TextStyle(fontFamily = PrognosFontFamily),
    bodyMedium = TextStyle(fontFamily = PrognosFontFamily),
    bodySmall = TextStyle(fontFamily = PrognosFontFamily),
    labelLarge = TextStyle(fontFamily = PrognosFontFamily),
    labelMedium = TextStyle(fontFamily = PrognosFontFamily),
    labelSmall = TextStyle(fontFamily = PrognosFontFamily)
)

data class AppColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val grey: Color,
    val grey2: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val isDark: Boolean = false
)

private val LightAppColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4F4F5),
    ink = Color(0xFF111111),
    grey = Color(0xFF6B7280),
    grey2 = Color(0xFF9CA3AF),
    border = Color(0xFFE5E7EB),
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF)
)

private val DarkAppColors = AppColors(
    background = Color(0xFF0B0B0B),
    surface = Color(0xFF161616),
    surface2 = Color(0xFF1F1F1F),
    ink = Color(0xFFF5F5F5),
    grey = Color(0xFF9CA3AF),
    grey2 = Color(0xFF6B7280),
    border = Color(0xFF2A2A2A),
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF111111),
    isDark = true
)

val LocalAppColors = compositionLocalOf { LightAppColors }

@Composable
fun appColors(): AppColors = LocalAppColors.current

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Lime,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF6B7280),
    error = AskRed
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF111111),
    secondary = Lime,
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF9CA3AF),
    error = AskRed
)

@Composable
fun PrognosTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PrognosTypography,
            content = content
        )
    }
}

fun sectorColor(code: String?): Color {
    return when (code) {
        "BTC" -> Color(0xFFB08A00)
        "ETH" -> Color(0xFF5E548E)
        "XRP" -> Color(0xFF2F6690)
        "SOL" -> Color(0xFF3D7A5E)
        "DOGE" -> Color(0xFFA9713F)
        "BNB" -> Color(0xFF7A7D2B)
        "HYPE" -> Color(0xFF9C5B7A)
        "ZEC" -> Color(0xFF5F7A8A)
        else -> Color(0xFF6B7280)
    }
}
