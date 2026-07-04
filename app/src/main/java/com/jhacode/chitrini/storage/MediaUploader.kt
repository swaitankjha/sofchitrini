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
        val fileId: String,
        val encryptedKey: String,
        val iv: String,
        val mimeType: String,
        val fileSize: Long,
        val originalFileName: String? = null
    )

    suspend fun uploadMedia(
        context: Context,
        uri: Uri,
        mimeType: String,
        onProgress: (Double) -> Unit = {}
    ): UploadResult? = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure Appwrite Session
            val userId = AppwriteManager.ensureSession()
            if (userId == null) {
                Log.e(TAG, "❌ Cannot upload: Appwrite session failed")
                return@withContext null
            }

            // 2. Read bytes
            val originalBytes = context.contentResolver.openInputStream(uri)?.use { 
                it.readBytes() 
            } ?: return@withContext null
            
            val originalFileName = getFileName(context, uri)

            // 3. Compress if it's a static image (not GIF)
            val bytesToEncrypt = if (mimeType.startsWith("image/") && !mimeType.contains("gif")) {
                compressImage(originalBytes)
            } else {
                originalBytes
            }

            // 4. Encrypt locally
            val encryptedBundle = MediaCryptoManager.encrypt(bytesToEncrypt) ?: return@withContext null

            // 5. Create temporary file for Appwrite (with extension to satisfy Appwrite filters)
            val tempFile = File(context.cacheDir, "upload_" + ID.unique() + ".enc")
            FileOutputStream(tempFile).use { it.write(encryptedBundle.encryptedBytes) }

            // 6. Upload to Appwrite
            Log.d(TAG, "Uploading encrypted file (${bytesToEncrypt.size} bytes) as .enc to Appwrite...")
            val response = AppwriteManager.storage.createFile(
                bucketId = AppwriteConfig.APPWRITE_BUCKET_ID,
                fileId = ID.unique(),
                file = InputFile.fromFile(tempFile)
            )

            // 7. 🔥 OPTIMIZATION: Save decrypted version to local cache so sender sees it immediately
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
            Log.e(TAG, "❌ Media upload failed: ${e.message}", e)
            null
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
