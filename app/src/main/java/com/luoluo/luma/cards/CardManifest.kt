package com.luoluo.luma.cards

/**
 * 一张卡片的配置。对应之前JS版设计里manifest.json的字段，
 * 只是这次是编译时内置，用Kotlin数据类而不是运行时读的JSON文件。
 */
data class CardManifest(
    val id: String,
    val name: String,
    val type: CardType,
    val enabled: Boolean
)

enum class CardType {
    /** 有界面的卡片，比如记账、健康档案 */
    DISPLAY,
    /** 没有界面的后台功能卡片，比如自动化 */
    SERVICE
}
