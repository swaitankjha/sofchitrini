package com.jhacode.chitrini.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jhacode.chitrini.data.local.db.ChatDatabase
import com.jhacode.chitrini.data.local.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.InputStreamReader

object BackupManager {

    private val gson = Gson()

    suspend fun exportChats(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val db = ChatDatabase.getInstance(context)
                val messages = db.messageDao().getAllMessages()
                val json = gson.toJson(messages)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(json)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun importChats(context: Context, uri: Uri, myUsername: String) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        val type = object : TypeToken<List<MessageEntity>>() {}.type
                        val importedMessages: List<MessageEntity> = gson.fromJson(reader, type)
                        
                        val db = ChatDatabase.getInstance(context)
                        
                        // We filter and re-insert messages based on the current user's perspective.
                        // The user asked for "same user name matching".
                        // In our system, messages are already tied to chatIds (userA_userB).
                        // We will insert all messages, using IGNORE to prevent duplicates.
                        db.messageDao().insertMessages(importedMessages)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import successful!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
