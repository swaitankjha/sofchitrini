package com.jhacode.chitrini.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 🔥 Restoring the beloved Gold & Cream palette
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4AF37), // Gold
    onPrimary = Color(0xFF1F1B16),
    primaryContainer = Color(0xFF634D1F),
    onPrimaryContainer = Color(0xFFFFE1BC),
    secondary = Color(0xFFEBD9B4),
    onSecondary = Color(0xFF372E1F),
    background = Color(0xFF1A1A17),
    onBackground = Color(0xFFE6E2D9),
    surface = Color(0xFF1A1A17),
    onSurface = Color(0xFFE6E2D9),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCFC6B4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C7C38), // Classic Chitrini Gold
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E1B4),
    onPrimaryContainer = Color(0xFF332000),
    secondary = Color(0xFF6F5A2C),
    onSecondary = Color.White,
    background = Color(0xFFFFF8E7), // Rich Cream
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8E7),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF5E9DC),
    onSurfaceVariant = Color(0xFF4D4633)
)

// 🔥 Reduced Font Sizes for a cleaner look
private val ChitriniTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp, // Reduced
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, // Reduced
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp, // Reduced
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp, // Reduced
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun ChitriniTheme(
    themeMode: String = "System",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChitriniTypography,
        content = content
    )
}
