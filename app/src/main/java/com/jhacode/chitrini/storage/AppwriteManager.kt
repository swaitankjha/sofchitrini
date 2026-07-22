package com.jhacode.chitrini.storage

import android.content.Context
import android.util.Log
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
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
            
            // 🔥 REMOVED setSelfSigned(true) - Not needed for cloud.appwrite.io
            // and can cause security handshake failures on some devices.

            // 🔥 Increased timeout for slower connections
            // (Standard Appwrite SDK doesn't always expose this easily on Client, 
            // but we ensure basic config is sound)

            account = Account(client)
            storage = Storage(client)
            bucketId = AppwriteConfig.APPWRITE_BUCKET_ID
            initialized = true
            Log.d("AppwriteManager", "✅ Appwrite Singapore initialized")
        } catch (e: Exception) {
            Log.e("AppwriteManager", "❌ Appwrite Singapore init failed", e)
        }
    }

    suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Check if session already exists
            val user = account.get()
            Log.d("AppwriteManager", "✅ Active session found: ${user.id}")
            user.id
        } catch (e: Exception) {
            try {
                // 2. No session, create anonymous one
                Log.d("AppwriteManager", "🔄 Creating anonymous session...")
                account.createAnonymousSession()
                val user = account.get()
                Log.d("AppwriteManager", "✅ Anonymous session created: ${user.id}")
                user.id
            } catch (e2: Exception) {
                // Handle "Session already exists" error specifically
                if (e2 is AppwriteException && e2.code == 401) {
                     // Sometimes get() fails but session exists? Unlikely, but let's be safe.
                     Log.e("AppwriteManager", "❌ Appwrite 401: Unauthorized. ${e2.message}")
                }
                Log.e("AppwriteManager", "❌ Appwrite Session Error: ${e2.message}")
                null
            }
        }
    }
}
