package com.luoluo.luma.memory

/**
 * 1g：这条记忆归谁——决定谁能看到它，不需要额外的visibility字段，
 * 直接从ownerType推出可见范围：
 * - USER：所有角色都能看到（比如"用户不吃辣"这种，谁都该知道）
 * - ROLE：只有这个角色自己能看到（ownerRoleId标记是哪个角色）
 * - RELATIONSHIP：只有"用户+这个角色"这一对能看到（ownerRoleId标记是跟哪个角色的关系；
 *   用户目前是单例，不需要额外存userId）
 */
enum class OwnerType {
    USER,
    ROLE,
    RELATIONSHIP
}

/**
 * 1g：极简版只分两档，不做三抽屉那套自动升降级/衰减/话题池：
 * - ALWAYS：每次对话都直接塞进system prompt里，AI一样一直"记得"
 * - COLD：不自动带上，只是存着，以后想在界面上手动翻看
 */
enum class Drawer {
    ALWAYS,
    COLD
}

data class MemoryCard(
    val id: String,
    val title: String,
    val content: String,
    val ownerType: OwnerType,
    /** ownerType是ROLE或RELATIONSHIP的时候才有意义，标记关联哪个角色；ownerType是USER时为null */
    val ownerRoleId: String? = null,
    val drawer: Drawer
)