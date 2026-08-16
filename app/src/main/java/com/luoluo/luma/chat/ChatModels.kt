package com.luoluo.luma.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * 一条provider配置。对应旧JS版 settings.providers 数组里的一项。
 * apiFormat 决定走 OpenAI 兼容格式还是 Anthropic 原生格式。
 */
data class ProviderConfig(
    val id: String,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    val apiFormat: ApiFormat
)

enum class ApiFormat {
    OPENAI,
    ANTHROPIC
}

/**
 * role沿用旧版语义：system / user / assistant。
 *
 * content是MutableState而不是普通var——这是关键。之前用普通var + "把整条消息
 * 换成新对象塞回列表"这招去让Compose感知变化，实测不够可靠（网络快的时候
 * 界面会卡住不刷新，得手动切后台再切回来才刷出来）。改成MutableState后，
 * 界面上读 msg.content.value 的地方会被Compose直接跟踪，content一变就立刻重画
 * 对应的那一小块，不用再折腾整条消息对象的替换。
 *
 * 没有isFailed这种消息级失败标记——讨论过带警告图标+重试按钮的方案，
 * 最后放弃了，改成"失败了就把消息从列表里退回输入框"这个更简洁的思路，
 * 不需要给消息本身挂状态，见ChatViewModel.performReply()。
 */
data class ChatMessage(
    val role: String,
    val content: MutableState<String> = mutableStateOf("")
) {
    constructor(role: String, content: String) : this(role, mutableStateOf(content))
}

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Sending : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
