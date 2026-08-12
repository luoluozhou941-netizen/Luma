package com.luoluo.luma.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luoluo.luma.storage.ChatMessageEntity
import com.luoluo.luma.storage.RoleDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1b+1c的ViewModel。
 *
 * provider配置目前还是内存里的几个可编辑字段，不落盘、不做管理界面——
 * 这一步只求"能对话+记录能存住"，正式的provider管理和多用途模型调度(usageBindings)留给后面。
 *
 * 1c新增：改成AndroidViewModel是为了能拿到Context去开Room数据库。
 * 数据库按角色隔离，这一步只有一个默认角色(RoleDatabaseManager.DEFAULT_ROLE_ID)，
 * 角色切换功能做好之后，这里换成读取"当前角色id"就行，其余存取逻辑不用改。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = RoleDatabaseManager
        .getDatabase(application, RoleDatabaseManager.DEFAULT_ROLE_ID)
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
        // 启动时把上次的聊天记录读回来，验证"app杀掉重开，记录还在"就靠这段
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
}
