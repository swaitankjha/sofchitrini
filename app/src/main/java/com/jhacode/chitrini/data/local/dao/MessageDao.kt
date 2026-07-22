package com.jhacode.chitrini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jhacode.chitrini.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        ORDER BY timestamp DESC
    """)
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): com.jhacode.chitrini.data.local.entity.MessageEntity?

    @Query("UPDATE messages SET text = :newText WHERE messageId = :messageId")
    suspend fun updateMessageText(messageId: String, newText: String)

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("""
        UPDATE messages 
        SET text = '[deleted_by_sender]', 
            messageType = 'TEXT',
            fileId = NULL,
            encryptedKey = NULL,
            iv = NULL,
            mimeType = NULL,
            fileSize = NULL,
            originalFileName = NULL
        WHERE messageId = :messageId
    """)
    suspend fun markAsDeleted(messageId: String)

    @Query("UPDATE messages SET reactions = :reactionsJson WHERE messageId = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: String?)

    @Query("UPDATE messages SET text = :newText, isEdited = 1 WHERE messageId = :messageId")
    suspend fun editMessage(messageId: String, newText: String)
}
