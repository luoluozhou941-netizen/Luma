package com.luoluo.luma.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 一条持久化的聊天消息。这是数据库层的实体，跟chat包里UI用的ChatMessage是分开的——
 * UI那份content是MutableState，是给Compose用的；这份是纯数据，存进数据库用的。
 * ChatViewModel负责在两者之间转换，两边互不依赖对方的类，保持底座和存储层解耦。
 */
@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long
)

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
