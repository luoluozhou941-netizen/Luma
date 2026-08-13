package com.luoluo.luma.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * 卡片自己的小数据存这里，跟聊天记录(messages表)分开。
 * key建议加卡片id前缀，比如"test-card:count"，避免不同卡片之间key撞车——
 * 这条约定写在CardHostApi的文档里，数据库层本身不做强制隔离。
 */
@Entity(tableName = "card_kv")
data class CardKeyValueEntity(
    @PrimaryKey
    val key: String,
    val value: String
)

@Dao
interface CardKeyValueDao {
    @Upsert
    suspend fun set(entity: CardKeyValueEntity)

    @Query("SELECT * FROM card_kv WHERE `key` = :key")
    suspend fun get(key: String): CardKeyValueEntity?
}
