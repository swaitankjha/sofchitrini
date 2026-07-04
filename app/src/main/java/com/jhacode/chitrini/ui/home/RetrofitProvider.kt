package com.jhacode.chitrini.ui.home

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {

    val quoteApi: QuoteApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://chitrini.netlify.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuoteApi::class.java)
    }
}