package com.jhacode.chitrini.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.jhacode.chitrini.data.local.db.ChatDatabase
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.repository.MainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val sender = intent.getStringExtra("sender") ?: return

        if (action == "ACTION_MARK_AS_READ") {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                val db = ChatDatabase.getInstance(context)
                val repository = ChatRepositoryImpl(db.messageDao(), db.chatDao())
                val myUsername = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE).getString("username", "") ?: ""
                val chatId = if (myUsername < sender) "${myUsername}_$sender" else "${sender}_$myUsername"
                
                // Mark all unread messages from this sender as seen
                val messages = repository.observeMessages(chatId).first()
                messages.forEach { msg ->
                    if (msg.sender == sender && msg.status != "seen") {
                        MainRepository.getInstance().sendSeenReceipt(sender, msg.messageId)
                        repository.updateMessageStatus(msg.messageId, "seen")
                    }
                }
            }
        }
        
        // Signal service to reset style/history for this user
        val serviceIntent = Intent(context, ChitriniService::class.java).apply {
            this.action = "ACTION_CLEAR_NOTIFICATIONS"
            putExtra("username", sender)
        }
        context.startService(serviceIntent)
        NotificationManagerCompat.from(context).cancel(sender.hashCode())
    }
}
