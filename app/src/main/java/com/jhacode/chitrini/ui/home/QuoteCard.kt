package com.jhacode.chitrini.ui.home

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhacode.chitrini.utils.AppState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuoteCard() {
    val localQuotes = listOf(
        "Pratyakṣaṁ kim anyat?",
        "Yatra yogeśvaraḥ kṛṣṇaḥ.",
        "Ananya Prema",
        "Atmanam viddhi."
    )

    var quotes by remember { mutableStateOf(localQuotes) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitProvider.quoteApi.getQuotes()
            if (response.quotes.isNotEmpty()) {
                quotes = response.quotes
            }
        } catch (e: Exception) {
            Log.e("QuoteFetch", "Fetch failed", e)
        }
    }

    val quote = remember(quotes) { quotes.random() }

    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp) // 🔥 Tight padding for integration
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 400) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now

                    when (tapCount) {
                        2 -> {
                            scope.launch {
                                delay(350)
                                if (tapCount == 2) {
                                    AppState.isHiddenChatsVisible = !AppState.isHiddenChatsVisible
                                    if (AppState.isHiddenChatsVisible) {
                                        AppState.isSelectionModeActive = false
                                    }
                                    tapCount = 0
                                }
                            }
                        }
                        3 -> {
                            AppState.isSelectionModeActive = !AppState.isSelectionModeActive
                            if (AppState.isSelectionModeActive) {
                                AppState.isHiddenChatsVisible = true
                            }
                            tapCount = 0
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = quote,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp, // 🔥 Slightly larger
                color = MaterialTheme.colorScheme.primary, // More visible Gold
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )
        )
    }
}
