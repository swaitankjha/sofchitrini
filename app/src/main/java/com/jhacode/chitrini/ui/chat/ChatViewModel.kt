package com.jhacode.chitrini.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.storage.MediaDownloader
import com.jhacode.chitrini.storage.MediaUploader
import com.jhacode.chitrini.utils.EncryptionManager
import com.jhacode.chitrini.utils.MessageSignal
import com.jhacode.chitrini.utils.ProfilePicData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
import java.util.*

class ChatViewModel(
    private val chatId: String,
    val myUsername: String,
    private val repository: ChatRepositoryImpl
) : ViewModel() {

    // 🔥 Better matching to ensure we get the other user correctly
    val otherUsername: String = chatId.split("_")
        .map { it.trim() }
        .firstOrNull { it != myUsername.trim() } ?: ""

    private var otherPublicKey: String? = null
    private val gson = Gson()

    var otherUserStatus by mutableStateOf("offline")
    var isOtherUserDeleted by mutableStateOf(false)
    
    // 🔥 Typing State
    var isOtherUserTyping by mutableStateOf(false)

    // Media and UI state
    var isUploading by mutableStateOf(false)
    var otherProfileFile by mutableStateOf<File?>(null)
    
    // 🔥 Reply State
    var replyingTo by mutableStateOf<MessageEntity?>(null)

    // 🔥 Edit State
    var editingMessage by mutableStateOf<MessageEntity?>(null)

    // =========================
    // 💬 OBSERVE MESSAGES
    // =========================
    val messages = repository.observeMessages(chatId)

    // =========================
    // 🔌 INITIALIZATION
    // =========================
    fun init(context: Context) {
        // 🔥 Join socket room for typing events
        SocketManager.joinChat(chatId)

        // Run lookups in IO
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Fetch other user's public key
            MainRepository.getInstance().getPublicKey(otherUsername) { key ->
                if (key != null) {
                    otherPublicKey = key
                    isOtherUserDeleted = false
                } else {
                    isOtherUserDeleted = true
                }
            }

            // 2. Observe Online Status
            MainRepository.getInstance().observeUserStatus(otherUsername) { status ->
                otherUserStatus = status
            }

            // 3. Mark existing unread messages as seen
            markAllAsSeen()
            
            // 4. Fetch other user's profile pic
            fetchOtherProfilePic(context)
            
            // 5. Observe Typing Status via Firebase (Robust)
            MainRepository.getInstance().observeUserTyping(otherUsername) { isTyping ->
                isOtherUserTyping = isTyping
            }
        }
    }

    fun setTyping(isTyping: Boolean, context: Context) {
        val prefs = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_typing_status", true)) {
            if (isTyping) return
        }
        
        MainRepository.getInstance().setTypingStatus(otherUsername, isTyping)
    }

    private suspend fun markAllAsSeen() {
        val currentMessages = messages.first()
        currentMessages.forEach { msg ->
            if (msg.sender != myUsername && msg.status != "seen") {
                MainRepository.getInstance().sendSeenReceipt(otherUsername, msg.messageId)
                repository.updateMessageStatus(msg.messageId, "seen")
            }
        }
    }

    private fun fetchOtherProfilePic(context: Context) {
        if (otherUsername.isBlank()) return
        
        MainRepository.getInstance().getProfilePic(otherUsername) { json ->
            if (json != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val data = gson.fromJson(json, ProfilePicData::class.java)
                        Log.d("ChatViewModel", "Fetching profile pic for $otherUsername, fileId: ${data.fileId}")
                        
                        val file = MediaDownloader.downloadMedia(context, data.fileId, data.encryptedKey, data.iv)
                        if (file != null) {
                            withContext(Dispatchers.Main) {
                                otherProfileFile = file
                                Log.d("ChatViewModel", "Profile pic loaded: ${file.absolutePath}")
                            }
                        } else {
                            Log.e("ChatViewModel", "Download failed for $otherUsername")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Profile pic parsing failed", e)
                    }
                }
            } else {
                Log.d("ChatViewModel", "No profile pic found for $otherUsername in Firebase")
            }
        }
    }

    // =========================
    // 📤 SEND MESSAGE (TEXT)
    // =========================
    fun sendMessage(text: String) {
        if (text.isBlank() || isOtherUserDeleted) return

        if (editingMessage != null) {
            val msgToEdit = editingMessage!!
            editingMessage = null
            
            viewModelScope.launch(Dispatchers.IO) {
                repository.editMessage(msgToEdit.messageId, text.trim())
                val signal = MessageSignal(
                    messageId = msgToEdit.messageId,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis(),
                    isEdit = true
                )
                val signalJson = gson.toJson(signal)
                val enc = EncryptionManager.encrypt(signalJson, otherPublicKey) ?: signalJson
                MainRepository.getInstance().sendChatMessage(otherUsername, enc, msgToEdit.messageId)
            }
            return
        }

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val optimisticMessage = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            sender = myUsername,
            text = text.trim(),
            timestamp = timestamp,
            status = "sent",
            messageType = "TEXT",
            replyToId = replyingTo?.messageId,
            replyToSender = replyingTo?.sender,
            replyToText = replyingTo?.let { if (it.messageType == "TEXT") it.text else "[${it.messageType}]" },
            replyToImageId = replyingTo?.fileId,
            replyToImageKey = replyingTo?.encryptedKey,
            replyToImageIv = replyingTo?.iv
        )

        val currentReplyingTo = replyingTo // Capture for signal
        replyingTo = null // Clear after sending

        viewModelScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                repository.sendMessage(optimisticMessage)
            }

            val signal = MessageSignal(
                messageId = messageId, 
                text = text.trim(), 
                timestamp = timestamp, 
                type = "TEXT",
                replyToId = currentReplyingTo?.messageId,
                replyToSender = currentReplyingTo?.sender,
                replyToText = currentReplyingTo?.let { if (it.messageType == "TEXT") it.text else "[${it.messageType}]" },
                replyToImageId = currentReplyingTo?.fileId,
                replyToImageKey = currentReplyingTo?.encryptedKey,
                replyToImageIv = currentReplyingTo?.iv
            )
            val signalJson = gson.toJson(signal)
            val encryptedSignal = EncryptionManager.encrypt(signalJson, otherPublicKey) ?: signalJson

            withContext(Dispatchers.IO) {
                MainRepository.getInstance().sendChatMessage(otherUsername, encryptedSignal, messageId)
            }
        }
    }

    // =========================
    // 📤 SEND MEDIA (IMAGE, etc)
    // =========================
    fun sendMedia(context: Context, uri: Uri, mimeType: String, caption: String? = null) {
        if (isOtherUserDeleted) return
        
        isUploading = true

        viewModelScope.launch(Dispatchers.IO) {
            val result = MediaUploader.uploadMedia(context, uri, mimeType)
            
            withContext(Dispatchers.Main) { 
                isUploading = false 
                if (result == null) {
                    Toast.makeText(context, "Failed to upload media", Toast.LENGTH_SHORT).show()
                }
            }

            if (result != null) {
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                val type = if (mimeType.startsWith("image/")) "IMAGE" 
                           else if (mimeType.startsWith("audio/")) "VOICE"
                           else if (mimeType.startsWith("video/")) "VIDEO"
                           else "FILE"

                val optimisticMessage = MessageEntity(
                    messageId = messageId,
                    chatId = chatId,
                    sender = myUsername,
                    text = caption?.trim() ?: "[Media]",
                    timestamp = timestamp,
                    status = "sent",
                    messageType = type,
                    fileId = result.fileId,
                    encryptedKey = result.encryptedKey,
                    iv = result.iv,
                    mimeType = result.mimeType,
                    fileSize = result.fileSize,
                    originalFileName = result.originalFileName,
                    replyToId = replyingTo?.messageId,
                    replyToSender = replyingTo?.sender,
                    replyToText = replyingTo?.let { if (it.messageType == "TEXT") it.text else "[${it.messageType}]" },
                    replyToImageId = replyingTo?.fileId,
                    replyToImageKey = replyingTo?.encryptedKey,
                    replyToImageIv = replyingTo?.iv
                )

                val currentReplyingTo = replyingTo
                replyingTo = null

                // 1. Save locally
                withContext(Dispatchers.IO) {
                    repository.sendMessage(optimisticMessage)
                }

                // 2. Prepare Signal
                val signal = MessageSignal(
                    messageId = messageId,
                    text = caption?.trim() ?: "[Media]",
                    timestamp = timestamp,
                    type = type,
                    fileId = result.fileId,
                    mimeType = result.mimeType,
                    encryptedKey = result.encryptedKey,
                    iv = result.iv,
                    fileSize = result.fileSize,
                    originalFileName = result.originalFileName,
                    replyToId = currentReplyingTo?.messageId,
                    replyToSender = currentReplyingTo?.sender,
                    replyToText = currentReplyingTo?.let { if (it.messageType == "TEXT") it.text else "[${it.messageType}]" },
                    replyToImageId = currentReplyingTo?.fileId,
                    replyToImageKey = currentReplyingTo?.encryptedKey,
                    replyToImageIv = currentReplyingTo?.iv
                )
                val signalJson = gson.toJson(signal)
                val encryptedSignal = EncryptionManager.encrypt(signalJson, otherPublicKey) ?: signalJson

                // 3. Send via Firebase
                withContext(Dispatchers.IO) {
                    MainRepository.getInstance().sendChatMessage(otherUsername, encryptedSignal, messageId)
                }
            }
        }
    }

    // =========================
    // 🔥 REACTIONS
    // =========================
    fun reactToMessage(message: MessageEntity, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Get current reactions
            val currentReactions = message.reactions?.let {
                try { gson.fromJson(it, Map::class.java) as MutableMap<String, String> } 
                catch (e: Exception) { mutableMapOf<String, String>() }
            } ?: mutableMapOf()

            // 2. Toggle Logic: If same emoji, remove it. If different, change it.
            val isRemoving = currentReactions[myUsername] == emoji
            if (isRemoving) {
                currentReactions.remove(myUsername)
            } else {
                currentReactions[myUsername] = emoji
            }
            
            val updatedJson = if (currentReactions.isEmpty()) null else gson.toJson(currentReactions)

            // 3. Save locally
            repository.updateReactions(message.messageId, updatedJson)

            // 4. Signal other user
            val signal = MessageSignal(
                messageId = message.messageId,
                text = "[Reaction]",
                timestamp = System.currentTimeMillis(),
                reactionEmoji = if (isRemoving) null else emoji,
                isReactionRemove = isRemoving
            )
            val signalJson = gson.toJson(signal)
            val enc = EncryptionManager.encrypt(signalJson, otherPublicKey) ?: signalJson
            MainRepository.getInstance().sendChatMessage(otherUsername, enc, message.messageId)
        }
    }

    fun deleteMessageForMe(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessageLocally(messageId)
        }
    }

    fun deleteMessageForEveryone(message: MessageEntity, context: Context) {
        val now = System.currentTimeMillis()
        if (now - message.timestamp > 30 * 60 * 1000) {
             Toast.makeText(context, "Too late to delete for everyone", Toast.LENGTH_SHORT).show()
             return
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.markAsDeleted(message.messageId, context)
            MainRepository.getInstance().sendDeleteSignal(otherUsername, message.messageId)
        }
    }

    fun removeMyProfilePic() {
        viewModelScope.launch(Dispatchers.IO) {
            MainRepository.getInstance().storeProfilePic(null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        SocketManager.leaveChat(chatId)
    }
}
