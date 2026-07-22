package com.jhacode.chitrini.storage

import android.content.Context
import android.util.Log
import com.jhacode.chitrini.crypto.MediaCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaDownloader {
    private const val TAG = "MediaDownloader"

    suspend fun downloadMedia(
        context: Context,
        fileId: String,
        encryptedKey: String,
        iv: String,
        onProgress: (Double) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val cachedFile = File(context.cacheDir, "media_$fileId")
        
        // 🔥 Robust cache check
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext cachedFile
        }

        try {
            // 1. Ensure Session
            val userId = AppwriteManager.ensureSession()
            if (userId == null) {
                Log.e(TAG, "❌ Download aborted: Appwrite session failed")
                return@withContext null
            }

            // 2. Download from Appwrite
            Log.d(TAG, "Downloading encrypted file: $fileId")
            val encryptedBytes = AppwriteManager.storage.getFileDownload(
                bucketId = AppwriteConfig.APPWRITE_BUCKET_ID,
                fileId = fileId
            )

            // 3. Decrypt locally
            val decryptedBytes = MediaCryptoManager.decrypt(
                data = encryptedBytes,
                keyBase64 = encryptedKey,
                ivBase64 = iv
            ) ?: return@withContext null

            // 4. Save to cache
            FileOutputStream(cachedFile).use { it.write(decryptedBytes) }

            Log.d(TAG, "✅ Media download & decryption success")
            cachedFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Media download failed: ${e.message}", e)
            null
        }
    }

    fun deleteFromCache(context: Context, fileId: String) {
        try {
            val file = File(context.cacheDir, "media_$fileId")
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "🗑️ Deleted $fileId from local cache")
            }
        } catch (e: Exception) {}
    }
}
