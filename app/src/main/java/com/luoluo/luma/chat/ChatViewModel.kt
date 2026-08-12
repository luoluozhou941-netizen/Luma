package com.luoluo.luma.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1b最小回路的ViewModel。
 *
 * provider配置目前是内存里的几个可编辑字段，不落盘、不做管理界面——
 * 这一步只求"能对话"，正式的provider管理和多用途模型调度(usageBindings)留给后面。
 */
class ChatViewModel : ViewModel() {

    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var model by mutableStateOf("")
    var apiFormat by mutableStateOf(ApiFormat.OPENAI)
    var useStreaming by mutableStateOf(true)

    var systemPrompt by mutableStateOf("你是Luma，一个温暖的AI陪伴助手。")

    val messages = mutableStateListOf<ChatMessage>()

    var inputText by mutableStateOf("")
    var uiState: ChatUiState by mutableStateOf(ChatUiState.Idle)
        private set

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        if (uiState is ChatUiState.Sending) return // 上一条还没发完，先不让连续发

        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            uiState = ChatUiState.Error("先把上面的baseUrl/apiKey/model填好")
            return
        }

        val userMsg = ChatMessage(role = "user", content = text)
        messages.add(userMsg)
        inputText = ""

        val aiMsg = ChatMessage(role = "assistant", content = "")
        messages.add(aiMsg)

        val cfg = ProviderConfig(
            id = "temp",
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModel = model,
            apiFormat = apiFormat
        )
        // 发给AI的历史不包含刚加进去的这条空assistant消息本身
        val historyForRequest = messages.filter { it !== aiMsg }

        uiState = ChatUiState.Sending

        viewModelScope.launch {
            try {
                if (useStreaming) {
                    AiClient.streamCallFlow(cfg, systemPrompt, historyForRequest)
                        .flowOn(Dispatchers.IO) // 网络读在IO线程跑，collect{}这块还是在主线程收
                        .collect { fullTextSoFar ->
                            aiMsg.content = fullTextSoFar
                            val idx = messages.indexOfFirst { it === aiMsg }
                            if (idx >= 0) messages[idx] = aiMsg.copy()
                        }
                } else {
                    val result = withContext(Dispatchers.IO) {
                        AiClient.callNonStream(cfg, systemPrompt, historyForRequest)
                    }
                    aiMsg.content = result
                    val idx = messages.indexOfFirst { it === aiMsg }
                    if (idx >= 0) messages[idx] = aiMsg.copy()
                }
                uiState = ChatUiState.Idle
            } catch (e: Exception) {
                uiState = ChatUiState.Error(e.message ?: "请求出错")
            }
        }
    }
}
