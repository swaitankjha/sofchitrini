package com.jhacode.chitrini.update.manager

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

class UpdateManager(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private const val TAG = "UpdateManager"
        private const val APK_NAME = "chitrini_update.apk"
    }

    @SuppressLint("Range")
    fun downloadAndInstall(
        url: String, 
        onProgress: (Int) -> Unit,
        onSignatureMismatch: () -> Unit, 
        onError: (String) -> Unit
    ) {
        if (url.isBlank()) {
            onError("Invalid download link")
            return
        }

        // Prepare destination
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)
        if (destination.exists()) destination.delete()

        try {
            Log.d(TAG, "🚀 Requesting download: $url")
            
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Chitrini Update")
                .setDescription("Downloading newer version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destination))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", "Mozilla/5.0")

            val downloadId = try {
                downloadManager.enqueue(request)
            } catch (e: Exception) {
                onError("Failed to start download. Please check if Download Manager is disabled.")
                return
            }

            Toast.makeText(context, "Update started. Check notification bar.", Toast.LENGTH_LONG).show()

            CoroutineScope(Dispatchers.IO).launch {
                var isComplete = false
                while (!isComplete) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        
                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                val bytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                if (total > 0) {
                                    val prog = ((bytes * 100L) / total).toInt()
                                    withContext(Dispatchers.Main) { onProgress(prog) }
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                                withContext(Dispatchers.Main) { onError("Download failed. Code: $reason") }
                                isComplete = true
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isComplete = true
                            }
                        }
                    }
                    cursor?.close()
                    delay(1000)
                }
            }

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        ctx.unregisterReceiver(this)
                        processDownloadedApk(destination, onSignatureMismatch, onError)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                @SuppressLint("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            onError("Update failed to start: ${e.message}")
        }
    }

    private fun processDownloadedApk(file: File, onSignatureMismatch: () -> Unit, onError: (String) -> Unit) {
        if (!file.exists() || file.length() == 0L) {
            onError("Update file missing after download")
            return
        }

        if (verifySignature(file)) {
            installApk(file)
        } else {
            file.delete()
            onSignatureMismatch()
        }
    }

    private fun verifySignature(apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            val currentSigs = getAppSignatures(context.packageName)
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }

            val apkSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pkgInfo?.signatures
            }

            if (currentSigs.isNullOrEmpty() || apkSigs.isNullOrEmpty()) return false
            currentSigs.any { cur -> apkSigs.any { dwn -> cur.toCharsString() == dwn.toCharsString() } }
        } catch (e: Exception) { false }
    }

    private fun getAppSignatures(packageName: String): Array<Signature>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
        } catch (e: Exception) { null }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install trigger failed", e)
        }
    }
}
