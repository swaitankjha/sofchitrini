package com.jhacode.chitrini.update.repository

import com.google.gson.Gson
import com.jhacode.chitrini.update.model.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException

class UpdateRepository(
    private val client: OkHttpClient,
    private val versionJsonUrl: String
) {
    private val gson = Gson()

    suspend fun fetchVersionInfo(): Result<VersionInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(versionJsonUrl)
            .header("Cache-Control", "no-cache") 
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val info = gson.fromJson(body, VersionInfo::class.java)
                Result.success(info)
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("No internet connection or server unreachable."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
