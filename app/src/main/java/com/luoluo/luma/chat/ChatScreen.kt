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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var settingsExpanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (settingsExpanded) {
            ProviderSettingsPanel(viewModel) { settingsExpanded = false }
        } else {
            TextButton(onClick = { settingsExpanded = true }) {
                Text("展开设置")
            }
        }

        val errorState = viewModel.uiState
        if (errorState is ChatUiState.Error) {
            Text(
                text = "出错了：${errorState.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

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

        InputBar(viewModel)
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

            TextButton(onClick = onCollapse, modifier = Modifier.fillMaxWidth()) {
                Text("收起")
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
                text = msg.content.ifEmpty { "…" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun InputBar(viewModel: ChatViewModel) {
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
            placeholder = { Text("对Luma说点什么…") }
        )
        Spacer(modifier = Modifier.width(8.dp))

        if (viewModel.uiState is ChatUiState.Sending) {
            Box(modifier = Modifier.padding(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
            }
        } else {
            Button(onClick = { viewModel.sendMessage() }) {
                Text("发送")
            }
        }
    }
}
