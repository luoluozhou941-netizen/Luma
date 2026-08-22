package com.luoluo.luma.user

import android.content.Context
import java.util.UUID

/**
 * 1g：用户档案的存取，单例——永远只有一份，不支持新建/切换/删除。
 * 跟RoleManager一样用SharedPreferences存，风格保持统一。
 */
object UserProfileManager {
    private const val PREFS_NAME = "luma_user_profile"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 取这份唯一的用户档案。第一次调用的时候本地还没有id，
     * 自动生成一个并存下来——之后每次调用都是同一个id、同一份档案。
     * 名字默认是空字符串，不强制填，跟角色卡"空白模板"的原则一致。
     */
    fun getProfile(context: Context): UserProfile {
        val p = prefs(context)
        var id = p.getString(KEY_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_ID, id).apply()
        }
        val name = p.getString(KEY_NAME, "") ?: ""
        return UserProfile(id = id, name = name)
    }

    fun updateName(context: Context, name: String) {
        // 保证id已经存在（正常情况下getProfile已经生成过了，这里是双重保险）
        getProfile(context)
        prefs(context).edit().putString(KEY_NAME, name).apply()
    }
}