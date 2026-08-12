package com.luoluo.luma.chat

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

/** role沿用旧版语义：system / user / assistant */
data class ChatMessage(
    val role: String,
    var content: String
)

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Sending : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
