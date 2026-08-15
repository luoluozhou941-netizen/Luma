package com.luoluo.luma.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luoluo.luma.storage.ChatMessageEntity
import com.luoluo.luma.storage.RoleDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1b+1c+1e的ViewModel。
 *
 * provider配置目前还是内存里的几个可编辑字段，不落盘、不做管理界面——
 * 这一步只求"能对话+记录能存住"，正式的provider管理和多用途模型调度(usageBindings)留给后面。
 *
 * 1e新增：roleId不再写死，是外面(ChatScreen)传进来的"当前激活角色"。
 * 切换角色的时候，ChatScreen那边会用roleId当key重新创建一个新的ChatViewModel实例，
 * 新实例的init块会自动去读对应角色数据库里的聊天记录——不用在这个类里额外写"切换角色"的逻辑，
 * 靠"整个ViewModel换一个新的"这种方式来达到同样效果，更不容易漏掉某个状态没重置。
 */
class ChatViewModel(application: Application, roleId: String) : AndroidViewModel(application) {

    private val dao = RoleDatabaseManager
        .getDatabase(application, roleId)
        .chatMessageDao()

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

    init {
        // 启动/切换角色时把这个角色的聊天记录读回来
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { dao.getAll() }
            saved.forEach { entity ->
                messages.add(ChatMessage(role = entity.role, content = entity.content))
            }
        }
    }

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
                            aiMsg.content.value = fullTextSoFar
                        }
                } else {
                    val result = withContext(Dispatchers.IO) {
                        AiClient.callNonStream(cfg, systemPrompt, historyForRequest)
                    }
                    aiMsg.content.value = result
                }
                uiState = ChatUiState.Idle

                // 收完整回复之后再落盘，两条一起存（用户那条+AI回的这条）
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    dao.insert(ChatMessageEntity(role = userMsg.role, content = userMsg.content.value, timestamp = now))
                    dao.insert(ChatMessageEntity(role = aiMsg.role, content = aiMsg.content.value, timestamp = now + 1))
                }
            } catch (e: Exception) {
                uiState = ChatUiState.Error(e.message ?: "请求出错")
            }
        }
    }

    companion object {
        /** ChatScreen那边按当前角色id创建对应的ViewModel实例，见ChatScreen.kt里的用法 */
        fun factory(application: Application, roleId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChatViewModel(application, roleId) }
            }
    }
}
