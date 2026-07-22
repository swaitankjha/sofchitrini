package com.jhacode.chitrini.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.core.net.toUri

@Composable
fun WallpaperBackground(
    uri: String?,
    stockColor: Int?,
    isBlur: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    Box(modifier = modifier.fillMaxSize()) {
        // 🔥 Background Content
        if (!uri.isNullOrEmpty()) {
            AsyncImage(
                model = uri.toUri(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isBlur) Modifier.blur(12.dp) else Modifier), // 🔥 Reduced blur radius for performance
                contentScale = ContentScale.Crop
            )
            // Intelligent readable overlay
            Box(
                Modifier.fillMaxSize().background(
                    if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f)
                )
            )
        } else if (stockColor != null && stockColor != 0) {
            // Direct use of ARGB Int
            Box(Modifier.fillMaxSize().background(Color(stockColor)))
        } else {
            // Default based on Theme
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        
        content()
    }
}
