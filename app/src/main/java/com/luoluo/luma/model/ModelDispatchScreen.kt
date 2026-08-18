package com.luoluo.luma.model

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luoluo.luma.chat.ApiFormat
import com.luoluo.luma.chat.ProviderConfig

/**
 * 1f：模型调度中心主界面。两块内容：
 * 1. provider列表——能存多条，同一个服务商也能存好几条不同配置
 * 2. 全局用途绑定——"对话/摘要/工具调用"这三个用途，各自默认绑哪条provider
 *
 * 角色层的覆盖不在这个界面——那个入口在角色管理里每个角色自己身上（见RoleModelOverrideScreen）。
 */
@Composable
fun ModelDispatchScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var providers by remember { mutableStateOf(ModelDispatchManager.getProviders(context)) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteBlockedMessage by remember { mutableStateOf<String?>(null) }
    var pickingUsageFor by remember { mutableStateOf<UsageType?>(null) }

    fun refresh() {
        providers = ModelDispatchManager.getProviders(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("模型调度", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("返回聊天") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Provider列表", style = MaterialTheme.typography.titleSmall)

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(providers) { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name.ifBlank { "（未命名）" }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (p.apiFormat == ApiFormat.ANTHROPIC) "Anthropic格式" else "OpenAI格式",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = { editingProvider = p }) { Text("编辑") }
                    TextButton(onClick = {
                        if (!ModelDispatchManager.deleteProvider(context, p.id)) {
                            deleteBlockedMessage = "「${p.name.ifBlank { "这条" }}」还被某个用途绑着，先去下面改绑定再删"
                        } else {
                            refresh()
                        }
                    }) { Text("删除") }
                }
            }
        }

        deleteBlockedMessage?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新增Provider")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("全局用途绑定", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))

        UsageType.entries.forEach { usage ->
            val boundId = ModelDispatchManager.getGlobalBinding(context, usage)
            val boundName = providers.find { it.id == boundId }?.name
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(usage.displayName, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingUsageFor = usage }) {
                    Text(boundName ?: "未绑定")
                }
            }
        }
    }

    // 新增provider
    if (showAddDialog) {
        ProviderEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, baseUrl, apiKey, model, format ->
                ModelDispatchManager.addProvider(context, name, baseUrl, apiKey, model, format)
                refresh()
                showAddDialog = false
            }
        )
    }

    // 编辑已有provider
    editingProvider?.let { p ->
        ProviderEditDialog(
            initial = p,
            onDismiss = { editingProvider = null },
            onSave = { name, baseUrl, apiKey, model, format ->
                ModelDispatchManager.updateProvider(
                    context,
                    p.copy(name = name, baseUrl = baseUrl, apiKey = apiKey, defaultModel = model, apiFormat = format)
                )
                refresh()
                editingProvider = null
            }
        )
    }

    // 选一条provider绑到某个用途上
    pickingUsageFor?.let { usage ->
        AlertDialog(
            onDismissRequest = { pickingUsageFor = null },
            title = { Text("「${usage.displayName}」绑定到哪个provider") },
            text = {
                Column {
                    TextButton(onClick = {
                        ModelDispatchManager.setGlobalBinding(context, usage, null)
                        pickingUsageFor = null
                    }) { Text("不绑定") }
                    providers.forEach { p ->
                        TextButton(onClick = {
                            ModelDispatchManager.setGlobalBinding(context, usage, p.id)
                            pickingUsageFor = null
                        }) { Text(p.name.ifBlank { "（未命名）" }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingUsageFor = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 新增/编辑provider共用的弹窗。initial传null是新增模式，传具体provider是编辑模式（字段预填）。
 */
@Composable
private fun ProviderEditDialog(
    initial: ProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String, model: String, format: ApiFormat) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.defaultModel ?: "") }
    var format by remember { mutableStateOf(initial?.apiFormat ?: ApiFormat.OPENAI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增Provider" else "编辑Provider") },
        text = {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名字（比如\"DeepSeek官方\"）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("baseUrl") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("apiKey") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = format == ApiFormat.OPENAI,
                            onClick = { format = ApiFormat.OPENAI },
                            label = { Text("OpenAI格式") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = format == ApiFormat.ANTHROPIC,
                            onClick = { format = ApiFormat.ANTHROPIC },
                            label = { Text("Anthropic格式") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                    onSave(name.trim(), baseUrl.trim(), apiKey.trim(), model.trim(), format)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
