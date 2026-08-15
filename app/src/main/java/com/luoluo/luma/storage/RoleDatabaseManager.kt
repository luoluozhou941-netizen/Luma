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
 * 1e加了角色管理界面(role包下)，角色的"名单"和"当前是谁"存在RoleManager里，
 * 这个类只管"给定一个roleId，打开/关闭/删除它对应的数据库文件"，不关心角色叫什么名字。
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
        )
            // 1d给数据库加了新表(版本1→2)。还在开发早期，本地测试数据不重要，
            // 遇到旧版本数据库直接重建，不做正式迁移脚本。等真正有用户数据了，
            // 这里要换成写清楚每一步的Migration，不能再用这个偷懒的策略。
            .fallbackToDestructiveMigration()
            .build()

        openDatabases[roleId] = db
        return db
    }

    /**
     * 删角色用的：先把开着的数据库连接关掉（不关就删文件，Windows这种系统会直接报错，
     * 安卓这边虽然一般能删掉，但残留一个开着的连接是隐患，还是老老实实先关再删），
     * 然后把这个角色的整个文件夹删掉，聊天记录、卡片数据跟着一起消失，删得干净。
     */
    @Synchronized
    fun closeAndDelete(context: Context, roleId: String) {
        openDatabases.remove(roleId)?.close()
        val roleDir = context.filesDir.resolve("roles").resolve(roleId)
        roleDir.deleteRecursively()
    }
}
