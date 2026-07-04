package com.jhacode.chitrini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jhacode.chitrini.data.local.model.ChatPreview
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query(
        """
        SELECT * FROM chat_preview
        ORDER BY lastTimestamp DESC
        """
    )
    fun observeChats(): Flow<List<ChatPreview>>

    @Query("SELECT * FROM chat_preview ORDER BY lastTimestamp DESC")
    suspend fun getChatListOnce(): List<ChatPreview>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatPreview)

    @Query("DELETE FROM chat_preview WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)
}
