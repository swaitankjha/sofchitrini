package com.jhacode.chitrini.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    Box(modifier = modifier.fillMaxSize()) {
        if (!uri.isNullOrEmpty()) {
            AsyncImage(
                model = uri.toUri(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isBlur) Modifier.blur(15.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            // Overlay to ensure text readability
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (isBlur) 0.1f else 0.05f)))
        } else if (stockColor != null) {
            Box(Modifier.fillMaxSize().background(Color(stockColor.toLong())))
        } else {
            // Default background if nothing is set
            Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E7)))
        }
        
        content()
    }
}
