package com.luoluo.luma.model

import android.content.Context
import com.luoluo.luma.chat.ApiFormat
import com.luoluo.luma.chat.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 1f：模型调度中心。管三样东西，跟RoleManager一样用SharedPreferences+JSON存
 * （数据量不大、结构简单，不值得为这个单独开数据库表）：
 *
 * 1. provider列表——可以存多条，同一个服务商也能存好几条不同配置
 *    （比如"DeepSeek官方"、"DeepSeek走OpenAI兼容"分开存，各自的baseUrl/apiKey/格式独立）
 * 2. 全局用途绑定——"对话"、"摘要"、"工具调用"这三个用途，各自默认绑哪条provider
 * 3. 角色层覆盖——某个角色如果某个用途想跟全局不一样，单独存一条覆盖；
 *    没被覆盖的用途，查询的时候自动落回全局绑定那条（见resolveProvider）
 *
 * 删除provider的规则：如果这条provider正被全局或者某个角色绑着，不允许删，
 * 要求先去改绑定——不做"删了自动变成空绑定"这种，那样容易让人在不知情的情况下
 * 突然发不出消息。
 */
object ModelDispatchManager {
    private const val PREFS_NAME = "luma_model_dispatch"
    private const val KEY_PROVIDERS = "providers_json"
    private const val KEY_GLOBAL_BINDINGS = "global_bindings_json"
    private const val KEY_ROLE_OVERRIDES = "role_overrides_json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── provider列表 ──

    fun getProviders(context: Context): List<ProviderConfig> {
        val json = prefs(context).getString(KEY_PROVIDERS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ProviderConfig(
                id = obj.getString("id"),
                name = obj.optString("name", ""),
                baseUrl = obj.getString("baseUrl"),
                apiKey = obj.getString("apiKey"),
                defaultModel = obj.getString("defaultModel"),
                apiFormat = ApiFormat.valueOf(obj.getString("apiFormat"))
            )
        }
    }

    private fun saveProviders(context: Context, providers: List<ProviderConfig>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("apiKey", p.apiKey)
                put("defaultModel", p.defaultModel)
                put("apiFormat", p.apiFormat.name)
            })
        }
        prefs(context).edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    /** 新增一条provider，id自动生成，不用调用方自己想id */
    fun addProvider(
        context: Context,
        name: String,
        baseUrl: String,
        apiKey: String,
        defaultModel: String,
        apiFormat: ApiFormat
    ): ProviderConfig {
        val newProvider = ProviderConfig(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModel = defaultModel,
            apiFormat = apiFormat
        )
        saveProviders(context, getProviders(context) + newProvider)
        return newProvider
    }

    fun updateProvider(context: Context, updated: ProviderConfig) {
        val providers = getProviders(context).map { if (it.id == updated.id) updated else it }
        saveProviders(context, providers)
    }

    /**
     * 删除失败返回false（说明这条还被绑着，调用方应该提示"先去改绑定"）。
     * 删除成功返回true。
     */
    fun deleteProvider(context: Context, providerId: String): Boolean {
        if (isProviderInUse(context, providerId)) return false
        saveProviders(context, getProviders(context).filter { it.id != providerId })
        return true
    }

    /** 检查这条provider有没有被全局绑定或者任何一个角色的覆盖绑定占用着 */
    fun isProviderInUse(context: Context, providerId: String): Boolean {
        val globalUsed = UsageType.entries.any { getGlobalBinding(context, it) == providerId }
        if (globalUsed) return true

        val overridesJson = prefs(context).getString(KEY_ROLE_OVERRIDES, null) ?: return false
        val obj = JSONObject(overridesJson)
        return obj.keys().asSequence().any { roleId ->
            val roleObj = obj.getJSONObject(roleId)
            roleObj.keys().asSequence().any { usageKey -> roleObj.getString(usageKey) == providerId }
        }
    }

    // ── 全局用途绑定 ──

    fun getGlobalBinding(context: Context, usage: UsageType): String? {
        val json = prefs(context).getString(KEY_GLOBAL_BINDINGS, null) ?: return null
        val obj = JSONObject(json)
        return if (obj.has(usage.name)) obj.getString(usage.name) else null
    }

    fun setGlobalBinding(context: Context, usage: UsageType, providerId: String?) {
        val json = prefs(context).getString(KEY_GLOBAL_BINDINGS, null)
        val obj = if (json != null) JSONObject(json) else JSONObject()
        if (providerId == null) {
            obj.remove(usage.name)
        } else {
            obj.put(usage.name, providerId)
        }
        prefs(context).edit().putString(KEY_GLOBAL_BINDINGS, obj.toString()).apply()
    }

    // ── 角色层覆盖 ──

    /** 返回null表示这个角色在这个用途上没有覆盖，跟着全局走 */
    fun getRoleOverride(context: Context, roleId: String, usage: UsageType): String? {
        val json = prefs(context).getString(KEY_ROLE_OVERRIDES, null) ?: return null
        val obj = JSONObject(json)
        if (!obj.has(roleId)) return null
        val roleObj = obj.getJSONObject(roleId)
        return if (roleObj.has(usage.name)) roleObj.getString(usage.name) else null
    }

    /** providerId传null表示清掉覆盖，改回跟随全局 */
    fun setRoleOverride(context: Context, roleId: String, usage: UsageType, providerId: String?) {
        val json = prefs(context).getString(KEY_ROLE_OVERRIDES, null)
        val obj = if (json != null) JSONObject(json) else JSONObject()
        val roleObj = if (obj.has(roleId)) obj.getJSONObject(roleId) else JSONObject()

        if (providerId == null) {
            roleObj.remove(usage.name)
        } else {
            roleObj.put(usage.name, providerId)
        }
        obj.put(roleId, roleObj)
        prefs(context).edit().putString(KEY_ROLE_OVERRIDES, obj.toString()).apply()
    }

    /**
     * 真正发消息的时候调这个：先看这个角色有没有针对这个用途的覆盖，
     * 有就用覆盖那条；没有就落回全局绑定；全局也没绑，返回null——
     * 调用方要提示用户去模型调度中心配置一下。
     */
    fun resolveProvider(context: Context, roleId: String, usage: UsageType): ProviderConfig? {
        val providers = getProviders(context)
        val overrideId = getRoleOverride(context, roleId, usage)
        val targetId = overrideId ?: getGlobalBinding(context, usage) ?: return null
        return providers.find { it.id == targetId }
    }
}
