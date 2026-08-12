package com.luoluo.luma.storage

import android.content.Context
import androidx.room.Room

/**
 * 每个AI角色一个独立的数据库文件，物理上放在各自的文件夹里，互不相通。
 * 目录结构：<应用私有目录>/roles/<roleId>/luma.db
 *
 * 删掉一个角色，只要把 roles/<roleId>/ 这整个文件夹删掉就行，
 * 不会影响到其他角色的数据——这是"各归各"这个决定在文件系统层面的体现。
 *
 * 1c范围内只有一个默认角色("default")，因为角色管理界面还没做（那是后面的事）。
 * 以后有了角色切换功能，调用方只要把roleId换成真实角色id，其余逻辑不用动。
 */
object RoleDatabaseManager {

    private val openDatabases = mutableMapOf<String, RoleDatabase>()

    const val DEFAULT_ROLE_ID = "default"

    @Synchronized
    fun getDatabase(context: Context, roleId: String = DEFAULT_ROLE_ID): RoleDatabase {
        openDatabases[roleId]?.let { return it }

        val roleDir = context.filesDir.resolve("roles").resolve(roleId)
        roleDir.mkdirs()
        val dbFile = roleDir.resolve("luma.db")

        val db = Room.databaseBuilder(
            context.applicationContext,
            RoleDatabase::class.java,
            dbFile.absolutePath
        ).build()

        openDatabases[roleId] = db
        return db
    }
}
