package com.catatan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.util.contrastColor
import com.catatan.app.util.darkenHex
import com.catatan.app.util.hexToRgb

/* ============================================================
   Design system — nilai dari style.css referensi
   ============================================================ */

fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color((0xFF shl 24) or clean.toInt(16))
            8 -> Color(clean.toLong(16).toInt())
            3 -> {
                val full = clean.map { "$it$it" }.joinToString("")
                Color((0xFF shl 24) or full.toInt(16))
            }
            else -> Color(0xFF5B7CFA)
        }
    } catch (_: Throwable) {
        Color(0xFF5B7CFA)
    }
}

fun rgba(hex: String, alpha: Float): Color {
    val (r, g, b) = hexToRgb(hex)
    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
        alpha = alpha
    )
}

/** Warna-warna aplikasi (terang & gelap). */
data class CatatanColors(
    val isDark: Boolean,
    val bg: Color,
    val bgElevated: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val sidebar: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val border: Color,
    val borderStrong: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val success: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val accentSofter: Color,
    val accentFg: Color,
    val scrim: Color
)

fun lightColors(accentHex: String): CatatanColors = CatatanColors(
    isDark = false,
    bg = Color(0xFFF3F4F8),
    bgElevated = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4F5F8),
    surface3 = Color(0xFFE9EBF1),
    sidebar = Color(0xFFEEF0F5),
    text = Color(0xFF1B1C20),
    text2 = Color(0xFF5F626D),
    text3 = Color(0xFF8B8E99),
    border = Color(0x17232630),
    borderStrong = Color(0x29232630),
    danger = Color(0xFFE5484D),
    dangerSoft = Color(0x1FE5484D),
    warn = Color(0xFFC2820A),
    success = Color(0xFF1A9B63),
    accent = parseHex(accentHex),
    accentStrong = parseHex(darkenHex(accentHex, 10)),
    accentSoft = rgba(accentHex, 0.14f),
    accentSofter = rgba(accentHex, 0.07f),
    accentFg = parseHex(contrastColor(accentHex)),
    scrim = Color(0x7A0C0E14)
)

fun darkColors(accentHex: String): CatatanColors = CatatanColors(
    isDark = true,
    bg = Color(0xFF0E1015),
    bgElevated = Color(0xFF161920),
    surface = Color(0xFF191C24),
    surface2 = Color(0xFF222630),
    surface3 = Color(0xFF2C313D),
    sidebar = Color(0xFF12151B),
    text = Color(0xFFF0F2F7),
    text2 = Color(0xFFA6AAB6),
    text3 = Color(0xFF757B89),
    border = Color(0x14FFFFFF),
    borderStrong = Color(0x26FFFFFF),
    danger = Color(0xFFFF6369),
    dangerSoft = Color(0x24FF6369),
    warn = Color(0xFFF5B544),
    success = Color(0xFF3DD68C),
    accent = parseHex(accentHex),
    accentStrong = parseHex(accentHex),
    accentSoft = rgba(accentHex, 0.14f),
    accentSofter = rgba(accentHex, 0.07f),
    accentFg = parseHex(contrastColor(accentHex)),
    scrim = Color(0x7A000000)
)

val LocalCatatanColors = staticCompositionLocalOf { lightColors("#5b7cfa") }

object CatatanTheme {
    val colors: CatatanColors
        @Composable @ReadOnlyComposable get() = LocalCatatanColors.current

    /** Radius baku */
    val rSm = 10.dp
    val r = 14.dp
    val rLg = 20.dp
    val rBtn = 11.dp
    val rSheet = 22.dp
    val rFab = 18.dp
    val rToolbar = 15.dp
    val rToast = 11.dp
}

@Composable
fun CatatanTheme(
    accentHex: String,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColors(accentHex) else lightColors(accentHex)

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentFg,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accentStrong,
            secondary = colors.accentStrong,
            onSecondary = colors.accentFg,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.text2,
            surfaceTint = colors.accent,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            errorContainer = colors.dangerSoft,
            onErrorContainer = colors.danger,
            scrim = colors.scrim
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentFg,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accentStrong,
            secondary = colors.accentStrong,
            onSecondary = colors.accentFg,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.text2,
            surfaceTint = colors.accent,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            errorContainer = colors.dangerSoft,
            onErrorContainer = colors.danger,
            scrim = colors.scrim
        )
    }

    val typography = Typography(
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
        titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.W700),
        titleMedium = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.W700),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600),
        labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.W500),
        labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.W500),
        headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W700)
    )

    val shapes = Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    )

    CompositionLocalProvider(LocalCatatanColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

/** Huruf sesuai pengaturan editor: sans | serif | mono */
fun fontFamilyOf(name: String?): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}
