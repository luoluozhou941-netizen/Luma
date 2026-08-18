package com.luoluo.luma.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luoluo.luma.cards.CardManifest
import com.luoluo.luma.cards.CardRegistry
import com.luoluo.luma.cards.CardType
import com.luoluo.luma.host.CardHostApiImpl
import com.luoluo.luma.role.RoleManager
import kotlinx.coroutines.launch

/**
 * 1e：加了roleId参数和onOpenRoleManager回调。
 * viewModel按roleId当key创建——切换角色时roleId变了，Compose会整个换一个新的ChatViewModel实例，
 * 新实例的init块自动去读那个角色的聊天记录，不用在这个Composable里手写"切换角色要重置什么状态"。
 *
 * 这次改动：只有一个"发送"按钮，回复是后台防抖触发的(见ChatViewModel)，界面上看不到倒计时。
 * 失败了不会在消息气泡上留任何标记——ViewModel会把消息退回输入框，这个Composable不用管这件事。
 * 报错通过errorEvents走Snackbar，自动出现自动消失，不用点确认。
 */
@Composable
fun ChatScreen(roleId: String, onOpenRoleManager: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: ChatViewModel = viewModel(
        key = roleId,
        factory = ChatViewModel.factory(application, roleId)
    )

    var settingsExpanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 输入框占位文字要跟着角色名字走，角色名字不常变，切角色的时候roleId会变，重新查一次就行
    val roleName = remember(roleId) {
        RoleManager.getRoles(context).find { it.id == roleId }?.name ?: "Luma"
    }

    LaunchedEffect(viewModel) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
        }
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenRoleManager) {
                    Text("角色管理")
                }
            }

            if (settingsExpanded) {
                ProviderSettingsPanel(viewModel) { settingsExpanded = false }
            } else {
                TextButton(onClick = { settingsExpanded = true }) {
                    Text("展开设置")
                }
            }

            // 1d+1e：卡片管理区，开关状态跟着当前角色走(roleId变了，重新读一遍这个角色的开关状态)。
            val hostApi = remember(context, roleId) { CardHostApiImpl(context, roleId) }
            val scope = rememberCoroutineScope()
            var cardStates by remember { mutableStateOf<List<Pair<CardManifest, Boolean>>>(emptyList()) }

            LaunchedEffect(roleId) {
                cardStates = CardRegistry.getAllManifests().map { it to CardRegistry.isCardEnabled(context, roleId, it) }
            }

            CardManagerPanel(cardStates) { cardId, newEnabled ->
                scope.launch {
                    CardRegistry.setCardEnabled(context, roleId, cardId, newEnabled)
                    cardStates = CardRegistry.getAllManifests().map { it to CardRegistry.isCardEnabled(context, roleId, it) }
                }
            }

            cardStates
                .filter { (manifest, enabled) -> enabled && manifest.type == CardType.DISPLAY }
                .forEach { (manifest, _) -> CardRegistry.RenderCard(manifest, hostApi) }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.messages) { msg ->
                    MessageBubble(msg)
                }
            }

            InputBar(viewModel, roleName = roleName, onSend = {
                settingsExpanded = false // 发送之后自动收起设置区，把屏幕还给聊天记录
                viewModel.sendMessage()
            })
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)
        ) { data ->
            // 默认Snackbar是深色警告样式（黑框白字），换成跟随主题的浅色调，
            // 不吓人、不挡视线，颜色会跟着系统动态取色和亮暗模式自动变。
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProviderSettingsPanel(viewModel: ChatViewModel, onCollapse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Provider设置（临时，不落盘）", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.baseUrl,
                onValueChange = { viewModel.baseUrl = it },
                label = { Text("baseUrl") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = viewModel.apiKey,
                onValueChange = { viewModel.apiKey = it },
                label = { Text("apiKey") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = viewModel.model,
                onValueChange = { viewModel.model = it },
                label = { Text("model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = viewModel.apiFormat == ApiFormat.OPENAI,
                    onClick = { viewModel.apiFormat = ApiFormat.OPENAI },
                    label = { Text("OpenAI格式") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = viewModel.apiFormat == ApiFormat.ANTHROPIC,
                    onClick = { viewModel.apiFormat = ApiFormat.ANTHROPIC },
                    label = { Text("Anthropic格式") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("流式返回", modifier = Modifier.weight(1f))
                Switch(
                    checked = viewModel.useStreaming,
                    onCheckedChange = { viewModel.useStreaming = it }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            // 倒计时时长：界面上不显示倒计时本身，但时长可以调，统一按秒存。
            // 想填分钟自己换算成秒填进去就行(比如2分钟填120)，不单独做分钟/秒切换的UI。
            var delayText by remember(viewModel.replyDelaySeconds) {
                mutableStateOf(viewModel.replyDelaySeconds.toString())
            }
            OutlinedTextField(
                value = delayText,
                onValueChange = { newValue ->
                    delayText = newValue
                    newValue.toIntOrNull()?.let { seconds ->
                        if (seconds > 0) viewModel.replyDelaySeconds = seconds
                    }
                },
                label = { Text("等待回复的秒数（比如填120就是2分钟）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            TextButton(onClick = onCollapse, modifier = Modifier.fillMaxWidth()) {
                Text("收起")
            }
        }
    }
}

@Composable
private fun CardManagerPanel(cardStates: List<Pair<CardManifest, Boolean>>, onToggle: (String, Boolean) -> Unit) {
    if (cardStates.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("卡片管理", style = MaterialTheme.typography.titleSmall)
            cardStates.forEach { (manifest, enabled) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(manifest.name, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { newValue -> onToggle(manifest.id, newValue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = msg.content.value.trim().ifEmpty { "…" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun InputBar(viewModel: ChatViewModel, roleName: String, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = viewModel.inputText,
            onValueChange = { viewModel.inputText = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("对${roleName}说点什么…") }
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (viewModel.uiState is ChatUiState.Sending) {
            Box(modifier = Modifier.padding(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
            }
        } else {
            Button(onClick = onSend) {
                Text("发送")
            }
        }
    }
}
