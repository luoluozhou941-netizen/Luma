package com.luoluo.luma.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1b+1c+1e的ViewModel，这次加了防抖回复机制。
 *
 * provider配置目前还是内存里的几个可编辑字段，不落盘、不做管理界面——
 * 这一步只求"能对话+记录能存住"，正式的provider管理和多用途模型调度(usageBindings)留给后面。
 *
 * 1e：roleId不再写死，是外面(ChatScreen)传进来的"当前激活角色"，切换角色时靠
 * viewModel(key=roleId)整个换一个新实例，init块自动读那个角色的聊天记录。
 *
 * 这次的核心改动——只有一个"发送"键，参考了旧JS版本(js/99-main.js)里
 * scheduleReply/cancelReply/triggerReply那套设计：
 * 1. 点发送：消息进列表，(重新)启动一个倒计时，界面上不显示这个倒计时
 * 2. 倒计时期间再发消息，倒计时重新算——只有真正停下来一段时间，AI才会接话
 * 3. 倒计时走完才调用AI，不是点发送立刻调用
 *
 * 失败处理：没有做消息级的失败标记/重试按钮(讨论过带警告图标的方案，最后放弃了)。
 * 失败了就把这一轮还没等到回复的消息从列表里"退"回输入框，撤掉空AI气泡，
 * 发送键本身没有任何特殊状态——用户看一眼退回来的文字，直接再点发送就是重试，
 * 不需要专门的重试机制，因为退回输入框这个动作本身就已经把状态复原了。
 */
class ChatViewModel(application: Application, roleId: String) : AndroidViewModel(application) {

    private val dao = RoleDatabaseManager
        .getDatabase(application, roleId)
        .chatMessageDao()

    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()

    var baseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var model by mutableStateOf("")
    var apiFormat by mutableStateOf(ApiFormat.OPENAI)
    var useStreaming by mutableStateOf(true)

    /** 倒计时时长，存秒。设置界面上不管用户想填分钟还是秒，最终都换算成这个存。 */
    var replyDelaySeconds by mutableIntStateOf(120)

    var systemPrompt by mutableStateOf("你是Luma，一个温暖的AI陪伴助手。")

    val messages = mutableStateListOf<ChatMessage>()

    var inputText by mutableStateOf("")
    var uiState: ChatUiState by mutableStateOf(ChatUiState.Idle)
        private set

    private var replyJob: Job? = null

    init {
        // 启动/切换角色时把这个角色的聊天记录读回来
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { dao.getAll() }
            saved.forEach { entity ->
                messages.add(ChatMessage(role = entity.role, content = entity.content))
            }
        }
    }

    /**
     * 点"发送"调这个。只做两件事：把消息加进列表、(重新)排一个倒计时。
     * 不在这里直接调AI——真正调AI是倒计时走完之后，在scheduleReply里触发的。
     */
    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty()) return

        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            _errorEvents.trySend("先把上面的baseUrl/apiKey/model填好")
            return
        }

        messages.add(ChatMessage(role = "user", content = text))
        inputText = ""

        scheduleReply()
    }

    private fun scheduleReply() {
        replyJob?.cancel() // 之前排的队还没到点，取消掉重新排——这就是"倒计时被重置"的实现
        replyJob = viewModelScope.launch {
            delay(replyDelaySeconds * 1000L)
            performReply()
        }
    }

    /** 这一轮还没等到AI回复的消息——从"上一条成功回复的assistant消息"往后数，都算 */
    private fun pendingUserMessages(): List<ChatMessage> {
        val lastAnsweredIndex = messages.indexOfLast { it.role == "assistant" && it.content.value.isNotBlank() }
        val pending = if (lastAnsweredIndex == -1) messages.toList() else messages.drop(lastAnsweredIndex + 1)
        return pending.filter { it.role == "user" }
    }

    private suspend fun performReply() {
        val pending = pendingUserMessages()
        if (pending.isEmpty()) return // 没有还没回复的消息，没什么好回的

        val aiMsg = ChatMessage(role = "assistant", content = "")
        messages.add(aiMsg)

        val cfg = ProviderConfig(
            id = "temp",
            baseUrl = baseUrl,
            apiKey = apiKey,
            defaultModel = model,
            apiFormat = apiFormat
        )
        val historyForRequest = messages.filter { it !== aiMsg }

        uiState = ChatUiState.Sending

        try {
            if (useStreaming) {
                AiClient.streamCallFlow(cfg, systemPrompt, historyForRequest)
                    .flowOn(Dispatchers.IO)
                    .collect { fullTextSoFar ->
                        aiMsg.content.value = fullTextSoFar
                    }
            } else {
                val result = withContext(Dispatchers.IO) {
                    AiClient.callNonStream(cfg, systemPrompt, historyForRequest)
                }
                aiMsg.content.value = result
            }

            // 请求没抛异常，不代表真的收到内容了——有些provider/格式不匹配的情况下，
            // 会"正常"走完整个流程但一个字都没解析出来，这种也按失败处理。
            if (aiMsg.content.value.isBlank()) {
                throw AiClientException("请求没报错，但没收到任何内容——多半是接口返回格式跟预期的对不上")
            }

            uiState = ChatUiState.Idle

            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                pending.forEachIndexed { i, msg ->
                    dao.insert(ChatMessageEntity(role = msg.role, content = msg.content.value, timestamp = now + i))
                }
                dao.insert(ChatMessageEntity(role = aiMsg.role, content = aiMsg.content.value, timestamp = now + pending.size))
            }
        } catch (e: Exception) {
            // 失败了：撤掉空AI气泡，把这一轮攒的消息从列表里"退"回输入框，
            // 发送键自然就是原来能点的样子，不用额外处理任何状态。
            messages.remove(aiMsg)
            val recoveredText = pending.joinToString("\n") { it.content.value }
            pending.forEach { messages.remove(it) }
            inputText = if (inputText.isBlank()) recoveredText else "$recoveredText\n$inputText"
            uiState = ChatUiState.Idle
            _errorEvents.trySend(e.message ?: "请求出错，但没有具体错误信息")
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
