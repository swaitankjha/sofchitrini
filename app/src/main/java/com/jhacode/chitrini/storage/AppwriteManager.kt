package com.jhacode.chitrini.storage

import android.content.Context
import android.util.Log
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppwriteManager {
    private var initialized = false
    lateinit var client: Client
    lateinit var account: Account
    lateinit var storage: Storage
    lateinit var bucketId: String

    fun init(context: Context) {
        if (initialized) return
        try {
            client = Client(context)
                .setEndpoint(AppwriteConfig.APPWRITE_ENDPOINT)
                .setProject(AppwriteConfig.APPWRITE_PROJECT_ID)
                .setSelfSigned(true)

            account = Account(client)
            storage = Storage(client)
            bucketId = AppwriteConfig.APPWRITE_BUCKET_ID
            initialized = true
            Log.d("AppwriteManager", "✅ Appwrite initialized successfully")
        } catch (e: Exception) {
            Log.e("AppwriteManager", "❌ Appwrite init failed", e)
        }
    }

    suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Try to get existing user
            val user = account.get()
            Log.d("AppwriteManager", "✅ Appwrite session active: ${user.id}")
            user.id
        } catch (e: Exception) {
            // 2. If no session, create one
            try {
                Log.d("AppwriteManager", "🔄 Creating anonymous Appwrite session...")
                account.createAnonymousSession()
                val newUser = account.get()
                Log.d("AppwriteManager", "✅ Appwrite session created: ${newUser.id}")
                newUser.id
            } catch (e2: Exception) {
                Log.e("AppwriteManager", "❌ Appwrite Auth Failed. Is Anonymous support enabled in Console? Error: ${e2.message}")
                null
            }
        }
    }
}
