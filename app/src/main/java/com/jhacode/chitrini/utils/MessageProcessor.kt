package com.jhacode.chitrini.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.repository.MainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MessageProcessor {
    private val gson = Gson()
    private val TAG = "MessageProcessor"

    fun processIncomingMessage(
        context: Context,
        scope: CoroutineScope,
        repository: ChatRepositoryImpl,
        model: DataModel,
        myUsername: String,
        onNotificationNeeded: (sender: String, text: String) -> Unit
    ) {
        scope.launch(Dispatchers.Default) {
            try {
                // 🔥 Step 1: Secure Decryption
                val decryptedJson = EncryptionManager.decrypt(model.data)
                
                if (decryptedJson == null) {
                    Log.e(TAG, "❌ Decryption failed. Ignoring message.")
                    return@launch
                }
                
                // 🔥 Step 2: Clean Parsing
                val signal = try {
                    gson.fromJson(decryptedJson, MessageSignal::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ JSON Parse error", e)
                    return@launch
                }

                val chatId = if (myUsername < model.sender) "${myUsername}_${model.sender}" else "${model.sender}_$myUsername"

                val entity = MessageEntity(
                    messageId = signal.messageId,
                    chatId = chatId,
                    sender = model.sender,
                    text = signal.text,
                    timestamp = signal.timestamp,
                    status = "received",
                    messageType = signal.type,
                    fileId = signal.fileId,
                    encryptedKey = signal.encryptedKey,
                    iv = signal.iv,
                    mimeType = signal.mimeType,
                    fileSize = signal.fileSize,
                    originalFileName = signal.originalFileName,
                    replyToId = signal.replyToId,
                    replyToSender = signal.replyToSender,
                    replyToText = signal.replyToText
                )

                // 3. Save to local Database
                repository.sendMessage(entity)

                // 4. Send Delivery Receipt
                MainRepository.getInstance().sendDeliveryReceipt(model.sender, signal.messageId)

                // 5. Handle UI Alerting using AppState
                val isChattingWithThisUser = AppState.isChatScreenActive && AppState.currentChatUser == model.sender
                
                if (!isChattingWithThisUser) {
                    val cleanNotificationText = when(signal.type) {
                        "IMAGE" -> "📷 Image"
                        "VIDEO" -> "🎥 Video"
                        "VOICE" -> "🎤 Voice Note"
                        "FILE" -> "📁 File"
                        else -> signal.text
                    }
                    onNotificationNeeded(model.sender, cleanNotificationText)
                } else {
                    // Mark as seen immediately if already looking at the chat
                    MainRepository.getInstance().sendSeenReceipt(model.sender, signal.messageId)
                    repository.updateMessageStatus(signal.messageId, "seen")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Critical processing failure", e)
            }
        }
    }
}
