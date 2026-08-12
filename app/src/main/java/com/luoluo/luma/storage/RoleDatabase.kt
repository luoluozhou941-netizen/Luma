package com.luoluo.luma.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RoleDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
}
