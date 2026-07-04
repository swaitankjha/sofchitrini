package com.jhacode.chitrini.ui.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
@Composable
fun QuoteCard() {
    val localQuotes = listOf(
        "Pratyakṣaṁ kim anyat?",
        "Yatra yogeśvaraḥ kṛṣṇaḥ.",
        "Ananya Prema",
        "Atmanam viddhi."
    )

    var quotes by remember {
        mutableStateOf(localQuotes)
    }



            LaunchedEffect(Unit) {
                try {
                    val response = RetrofitProvider.quoteApi.getQuotes()

                    Log.d("QuoteFetch", "Quotes: ${response.quotes}")

                    if (response.quotes.isNotEmpty()) {
                        quotes = response.quotes
                    }

                } catch (e: Exception) {
                    Log.e("QuoteFetch", "Fetch failed", e)
                }
            }

    val quote = remember(quotes) {
        quotes.random()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = quote,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.4.sp
            )
        )
    }
}
