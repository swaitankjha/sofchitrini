package com.jhacode.chitrini.data.repository

import com.jhacode.chitrini.data.local.dao.ChatDao
import com.jhacode.chitrini.data.local.dao.MessageDao
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.local.model.ChatPreview
import kotlinx.coroutines.flow.Flow

class ChatRepositoryImpl(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao // Added ChatDao
) {

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.observeMessages(chatId)
    }

    suspend fun sendMessage(message: MessageEntity) {
        // 1. Save message
        messageDao.insertMessage(message)

        // 2. Update chat preview
        val previewText = when(message.messageType) {
            "IMAGE" -> "📷 Image"
            "VOICE" -> "🎤 Voice note"
            "VIDEO" -> "🎥 Video"
            "FILE" -> "📁 File"
            else -> message.text
        }
            
        chatDao.insertChat(
            ChatPreview(
                chatId = message.chatId,
                userA = message.chatId.split("_")[0],
                userB = message.chatId.split("_")[1],
                lastMessage = previewText,
                lastTimestamp = message.timestamp
            )
        )
    }

    suspend fun clearMessages(chatId: String) {
        messageDao.deleteMessagesByChatId(chatId)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun getMessage(messageId: String) = messageDao.getMessageById(messageId)

    suspend fun markAsDeleted(messageId: String) {
        // 1. Update the message itself
        messageDao.updateMessageText(messageId, "[deleted_by_sender]")

        // 2. 🔥 Update the Chat Preview (Recent Menu)
        val msg = messageDao.getMessageById(messageId)
        if (msg != null) {
            chatDao.insertChat(
                com.jhacode.chitrini.data.local.model.ChatPreview(
                    chatId = msg.chatId,
                    userA = msg.chatId.split("_")[0],
                    userB = msg.chatId.split("_")[1],
                    lastMessage = "This message was deleted",
                    lastTimestamp = msg.timestamp
                )
            )
        }
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateMessageStatus(messageId, status)
    }
}
