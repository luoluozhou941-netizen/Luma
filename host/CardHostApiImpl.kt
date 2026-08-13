package com.luoluo.luma.host

import android.content.Context
import com.luoluo.luma.storage.CardKeyValueEntity
import com.luoluo.luma.storage.RoleDatabaseManager

/**
 * CardHostApi的实现。这个文件属于底座层，允许直接依赖storage包的内部实现——
 * "卡片不能直接依赖底座内部实现"这条规则约束的是cards包，不是这里。
 */
class CardHostApiImpl(context: Context, roleId: String = RoleDatabaseManager.DEFAULT_ROLE_ID) : CardHostApi {

    private val dao = RoleDatabaseManager.getDatabase(context, roleId).cardKeyValueDao()

    override suspend fun setValue(key: String, value: String) {
        dao.set(CardKeyValueEntity(key = key, value = value))
    }

    override suspend fun getValue(key: String): String? {
        return dao.get(key)?.value
    }
}
