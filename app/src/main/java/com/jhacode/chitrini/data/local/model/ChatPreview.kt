package com.jhacode.chitrini.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_preview")
data class ChatPreview(

    @PrimaryKey
    val chatId: String,

    val userA: String,
    val userB: String,

    val lastMessage: String,
    val lastTimestamp: Long
)
