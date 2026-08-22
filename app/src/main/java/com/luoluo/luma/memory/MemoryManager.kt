package com.luoluo.luma.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 1g：记忆卡片的存取。跟ModelDispatchManager同一套风格——
 * SharedPreferences存一个JSON数组，不上Room（这个阶段数据量不大，结构也简单）。
 *
 * 这是"骨架版"：只有存取+按角色查可见范围，没有检索排序、没有话题池、
 * 没有自动升降级——那些是远期蓝图里的东西，先不做。
 */
object MemoryManager {
    private const val PREFS_NAME = "luma_memory_cards"
    private const val KEY_CARDS = "cards_json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllCards(context: Context): List<MemoryCard> {
        val json = prefs(context).getString(KEY_CARDS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MemoryCard(
                id = obj.getString("id"),
                title = obj.getString("title"),
                content = obj.getString("content"),
                ownerType = OwnerType.valueOf(obj.getString("ownerType")),
                ownerRoleId = if (obj.has("ownerRoleId") && !obj.isNull("ownerRoleId")) obj.getString("ownerRoleId") else null,
                drawer = Drawer.valueOf(obj.getString("drawer"))
            )
        }
    }

    private fun saveCards(context: Context, cards: List<MemoryCard>) {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("content", c.content)
                put("ownerType", c.ownerType.name)
                put("ownerRoleId", c.ownerRoleId)
                put("drawer", c.drawer.name)
            })
        }
        prefs(context).edit().putString(KEY_CARDS, arr.toString()).apply()
    }

    fun addCard(
        context: Context,
        title: String,
        content: String,
        ownerType: OwnerType,
        ownerRoleId: String?,
        drawer: Drawer
    ): MemoryCard {
        val newCard = MemoryCard(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            ownerType = ownerType,
            ownerRoleId = ownerRoleId,
            drawer = drawer
        )
        saveCards(context, getAllCards(context) + newCard)
        return newCard
    }

    fun updateCard(context: Context, updated: MemoryCard) {
        saveCards(context, getAllCards(context).map { if (it.id == updated.id) updated else it })
    }

    fun deleteCard(context: Context, cardId: String) {
        saveCards(context, getAllCards(context).filter { it.id != cardId })
    }

    /**
     * 这个角色能看到哪些卡片：owner=用户的全都能看；owner=这个角色自己的能看；
     * owner=跟这个角色的关系的能看。别的角色的私有卡片、跟别的角色的关系卡片，看不到。
     */
    fun getVisibleCards(context: Context, roleId: String): List<MemoryCard> =
        getAllCards(context).filter { card ->
            card.ownerType == OwnerType.USER ||
                ((card.ownerType == OwnerType.ROLE || card.ownerType == OwnerType.RELATIONSHIP) && card.ownerRoleId == roleId)
        }

    /** 真正发消息的时候调这个：这个角色能看到的卡片里，drawer是ALWAYS的那些，要塞进system prompt */
    fun getAlwaysCardsForRole(context: Context, roleId: String): List<MemoryCard> =
        getVisibleCards(context, roleId).filter { it.drawer == Drawer.ALWAYS }
}