package com.jhacode.chitrini.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.jhacode.chitrini.crypto.MediaCryptoManager
import io.appwrite.ID
import io.appwrite.models.InputFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MediaUploader {
    private const val TAG = "MediaUploader"

    data class UploadResult(
        val fileId: String? = null,
        val encryptedKey: String? = null,
        val iv: String? = null,
        val mimeType: String? = null,
        val fileSize: Long = 0,
        val originalFileName: String? = null,
        val error: String? = null // 🔥 Added for debugging
    )

    suspend fun uploadMedia(
        context: Context,
        uri: Uri,
        mimeType: String,
        onProgress: (Double) -> Unit = {}
    ): UploadResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Starting upload for URI: $uri")
            
            // 1. Ensure Appwrite Session
            val userId = AppwriteManager.ensureSession()
            if (userId == null) {
                Log.e(TAG, "❌ Cannot upload: Appwrite session failed")
                return@withContext UploadResult(error = "Appwrite session failed. Check Anonymous Auth.")
            }

            // 2. Read bytes safely
            val originalBytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to read bytes from URI: $uri", e)
                null
            } ?: return@withContext UploadResult(error = "Failed to read file from storage.")
            
            Log.d(TAG, "📄 Read ${originalBytes.size} bytes")
            
            val originalFileName = getFileName(context, uri)

            // 3. Compress if it's a static image (not GIF)
            val bytesToEncrypt = if (mimeType.startsWith("image/") && !mimeType.contains("gif")) {
                compressImage(originalBytes)
            } else {
                originalBytes
            }

            // 4. Encrypt locally
            val encryptedBundle = MediaCryptoManager.encrypt(bytesToEncrypt) ?: run {
                Log.e(TAG, "❌ Encryption failed")
                return@withContext UploadResult(error = "Local encryption failed.")
            }

            // 5. Create temporary file for Appwrite
            val tempFile = File(context.cacheDir, "up_${System.currentTimeMillis()}.enc")
            FileOutputStream(tempFile).use { it.write(encryptedBundle.encryptedBytes) }

            // 6. Upload to Appwrite
            Log.d(TAG, "📤 Uploading to Appwrite bucket: ${AppwriteConfig.APPWRITE_BUCKET_ID}")
            
            val response = try {
                AppwriteManager.storage.createFile(
                    bucketId = AppwriteConfig.APPWRITE_BUCKET_ID,
                    fileId = ID.unique(),
                    file = InputFile.fromFile(tempFile)
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Appwrite Storage Error: ${e.message}")
                return@withContext UploadResult(error = "Appwrite: ${e.message}")
            }

            // 7. Save decrypted version to local cache
            val cachedFile = File(context.cacheDir, "media_${response.id}")
            FileOutputStream(cachedFile).use { it.write(bytesToEncrypt) }

            // Cleanup
            tempFile.delete()

            Log.d(TAG, "✅ Media upload success: ${response.id}")
            UploadResult(
                fileId = response.id,
                encryptedKey = encryptedBundle.keyBase64,
                iv = encryptedBundle.ivBase64,
                mimeType = mimeType,
                fileSize = bytesToEncrypt.size.toLong(),
                originalFileName = originalFileName
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Media upload fatal error: ${e.message}", e)
            UploadResult(error = "Fatal: ${e.message}")
        }
    }

    private fun compressImage(data: ByteArray): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return data
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        } catch (e: Exception) { data }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) { null }
    }
}
