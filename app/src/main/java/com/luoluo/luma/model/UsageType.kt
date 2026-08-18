package com.luoluo.luma.model

/**
 * 1f：模型调度中心要绑定的"用途"。先写死这三个，不做成用户能自己新增用途名字的
 * 可扩展列表——目前的实际场景这三个够用，做成可扩展平白多出一堆界面复杂度。
 * 以后真需要加新用途，直接在这个枚举里加一项就行。
 */
enum class UsageType(val displayName: String) {
    CHAT("对话"),
    SUMMARY("摘要"),
    TOOL_CALL("工具调用")
}
