package com.rynekryz.sniffsniff.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MonochromeLight = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E3E3),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Color(0xFF1B1B1B),
    tertiary = Color(0xFF767676),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF2B2B2B),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF474747),
    outline = Color(0xFF757575),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val MonochromeDark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2D2D2D),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFC7C7C7),
    onSecondary = Color(0xFF2E2E2E),
    secondaryContainer = Color(0xFF444444),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFADADAD),
    onTertiary = Color(0xFF353535),
    tertiaryContainer = Color(0xFF555555),
    onTertiaryContainer = Color(0xFFF0F0F0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF474747),
    onSurfaceVariant = Color(0xFFC7C7C7),
    outline = Color(0xFF8E8E8E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private fun sniffTypography(family: FontFamily?) = Typography(
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun SniffSniffTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    systemFont: Boolean = false,
    haptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MonochromeDark
        else -> MonochromeLight
    }
    val colors = if (darkTheme && pureBlack) {
        base.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF121212))
    } else base
    val family = if (systemFont) {
        FontFamily.Default
    } else {
        FontFamily(
            Font(familyName = DeviceFontFamilyName("google-sans-flex")),
            Font(familyName = DeviceFontFamilyName("google-sans-text")),
            Font(familyName = DeviceFontFamilyName("sans-serif-rounded")),
        )
    }
    CompositionLocalProvider(LocalHapticsEnabled provides haptics) {
        MaterialTheme(colorScheme = colors, typography = sniffTypography(family), content = content)
    }
}
