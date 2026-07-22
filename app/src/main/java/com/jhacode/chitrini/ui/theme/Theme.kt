package com.jhacode.chitrini.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 🔥 High-Contrast OLED Dark - Improved Visibility
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4AF37), 
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF332D21),
    onPrimaryContainer = Color(0xFFFFE1BC),
    secondary = Color(0xFFB0B0B0),
    background = Color.Black,
    surface = Color(0xFF161614),
    onSurface = Color(0xFFF0F0F0), // Pure bright for readability
    surfaceVariant = Color(0xFF262624),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color(0xFF444444),
    error = Color(0xFFFF5252)
)

// 🔥 Restored Classic Light (Cream & Gold) - Increased Contrast
private val LightColors = lightColorScheme(
    primary = Color(0xFF8B6B23),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECB3),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFF5D4037),
    background = Color(0xFFFFFDF0),
    surface = Color.White,
    onSurface = Color(0xFF121212), // Sharper black
    surfaceVariant = Color(0xFFF7F2E8),
    onSurfaceVariant = Color(0xFF3E2723),
    outline = Color(0xFFD7CCC8),
    error = Color(0xFFB71C1C)
)

private val ChitriniTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 10.sp, // Increased
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, // Increased
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp // Increased from 18
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp // Increased
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, // Increased from 15 (approx 0.5x felt scale)
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp, // Increased from 14
        lineHeight = 22.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp, // Increased from 10
        letterSpacing = 1.sp
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
