package com.jhacode.chitrini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String,          // uuid
    val chatId: String,             // rahul_neha
    val sender: String,             // @rahul
    val text: String,
    val timestamp: Long,
    val status: String,             // sent / delivered / seen
    val messageType: String = "TEXT",
    val fileId: String? = null,
    val encryptedKey: String? = null,
    val iv: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val originalFileName: String? = null,
    
    // 🔥 Reply Fields
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null
)
