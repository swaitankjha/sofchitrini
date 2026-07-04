package com.jhacode.chitrini.utils

/**
 * Production-grade data class for message signals.
 * This object is JSON-serialized and then ENTIRELY encrypted before transmission.
 */
data class MessageSignal(
    val messageId: String,
    val text: String,
    val timestamp: Long,
    val type: String = "TEXT", // TEXT, IMAGE, VOICE, VIDEO, FILE, MISSED_CALL
    val fileId: String? = null,
    val encryptedKey: String? = null,
    val iv: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val originalFileName: String? = null,
    
    // 🔥 Reply metadata
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null
)
