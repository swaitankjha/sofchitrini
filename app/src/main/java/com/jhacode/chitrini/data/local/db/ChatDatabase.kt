package com.jhacode.chitrini.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jhacode.chitrini.data.local.dao.ChatDao
import com.jhacode.chitrini.data.local.dao.MessageDao
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.local.model.ChatPreview

@Database(
    entities = [
        MessageEntity::class,
        ChatPreview::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chitrini_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
