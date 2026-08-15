package com.luoluo.luma.role

import android.content.Context
import com.luoluo.luma.storage.RoleDatabaseManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 角色的"名单"和"当前用的是哪个"存在这里。这是全局信息，不属于任何一个角色自己的数据库——
 * 每个角色的数据库(RoleDatabaseManager管的那个)只存它自己的聊天记录/卡片数据，
 * 不知道"自己叫什么名字"这种元信息，这类元信息只能存在角色数据库外面。
 *
 * 用SharedPreferences存，因为角色数量不多、结构简单，不值得为这点数据单独开一张全局表。
 */
object RoleManager {
    private const val PREFS_NAME = "luma_roles"
    private const val KEY_ROLES = "roles_json"
    private const val KEY_ACTIVE_ROLE_ID = "active_role_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 第一次用的时候，把1b-1d阶段一直在用的那个"default"角色补登记成一个正式角色，
     * 不然它攒的聊天记录/卡片数据就成了没人认领的孤儿。
     */
    private fun ensureSeeded(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_ROLES)) {
            val seedRole = RoleInfo(id = RoleDatabaseManager.DEFAULT_ROLE_ID, name = "默认角色")
            saveRoles(context, listOf(seedRole))
            p.edit().putString(KEY_ACTIVE_ROLE_ID, seedRole.id).apply()
        }
    }

    fun getRoles(context: Context): List<RoleInfo> {
        ensureSeeded(context)
        val json = prefs(context).getString(KEY_ROLES, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            RoleInfo(id = obj.getString("id"), name = obj.getString("name"))
        }
    }

    private fun saveRoles(context: Context, roles: List<RoleInfo>) {
        val arr = JSONArray()
        roles.forEach { role ->
            arr.put(JSONObject().apply {
                put("id", role.id)
                put("name", role.name)
            })
        }
        prefs(context).edit().putString(KEY_ROLES, arr.toString()).apply()
    }

    fun createRole(context: Context, name: String): RoleInfo {
        val newRole = RoleInfo(id = UUID.randomUUID().toString(), name = name)
        saveRoles(context, getRoles(context) + newRole)
        return newRole
    }

    /** 删掉角色的登记信息，也把它的数据库文件夹整个删掉——"各归各"的另一半：删得也干净，不留残渣 */
    fun deleteRole(context: Context, roleId: String) {
        val remaining = getRoles(context).filter { it.id != roleId }
        saveRoles(context, remaining)

        RoleDatabaseManager.closeAndDelete(context, roleId)

        if (getActiveRoleId(context) == roleId) {
            val fallback = remaining.firstOrNull()?.id
            if (fallback != null) {
                setActiveRoleId(context, fallback)
            } else {
                prefs(context).edit().remove(KEY_ACTIVE_ROLE_ID).apply()
            }
        }
    }

    fun getActiveRoleId(context: Context): String {
        ensureSeeded(context)
        return prefs(context).getString(KEY_ACTIVE_ROLE_ID, null) ?: RoleDatabaseManager.DEFAULT_ROLE_ID
    }

    fun setActiveRoleId(context: Context, roleId: String) {
        prefs(context).edit().putString(KEY_ACTIVE_ROLE_ID, roleId).apply()
    }
}
