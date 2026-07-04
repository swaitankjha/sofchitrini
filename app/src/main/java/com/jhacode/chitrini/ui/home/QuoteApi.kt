package com.jhacode.chitrini.ui.home

import retrofit2.http.GET

data class QuoteResponse(
    val quotes: List<String>
)

interface QuoteApi {

    @GET("quotes.json")
    suspend fun getQuotes(): QuoteResponse
}