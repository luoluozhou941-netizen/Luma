package com.luoluo.luma.user

/**
 * 1g：用户档案。当前设计是单例——不支持切换多用户，全局只有一份。
 * 以后如果有多用户场景（比如你和小人各自单独一份），再考虑扩展成列表，
 * 现在不做这个复杂度。
 *
 * id存在是因为"关系记忆"（用户和某个角色之间的记忆）需要一个稳定的id
 * 来拼出"这条记忆属于哪个用户+哪个角色"这一对，不是为了支持切换用户。
 */
data class UserProfile(
    val id: String,
    val name: String
)