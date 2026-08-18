package com.luoluo.luma.role

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.luoluo.luma.model.ModelDispatchManager
import com.luoluo.luma.model.UsageType

/**
 * 1f：某个角色的"模型覆盖"界面。三个用途各一行——
 * 没覆盖的显示"跟随全局（当前全局绑的是xxx）"，点开能选一条具体provider覆盖上去，
 * 也能选"跟随全局"把覆盖清掉。
 */
@Composable
fun RoleModelOverrideScreen(roleId: String, roleName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val providers = remember { ModelDispatchManager.getProviders(context) }
    var pickingUsage by remember { mutableStateOf<UsageType?>(null) }
    // 用这个数字触发重组，选完之后+1让下面的读取重新算一遍
    var refreshTick by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("「$roleName」的模型覆盖", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("返回") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "没覆盖的用途会跟着全局设置走，只有真正需要特殊化的用途才需要单独选",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        UsageType.entries.forEach { usage ->
            refreshTick // 读一下让Compose知道这块要跟着refreshTick变化重组
            val overrideId = ModelDispatchManager.getRoleOverride(context, roleId, usage)
            val globalId = ModelDispatchManager.getGlobalBinding(context, usage)
            val globalName = providers.find { it.id == globalId }?.name ?: "未绑定"
            val overrideName = providers.find { it.id == overrideId }?.name

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(usage.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (overrideName != null) "已覆盖为：$overrideName" else "跟随全局（当前是：$globalName）",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { pickingUsage = usage }) { Text("修改") }
            }
        }
    }

    pickingUsage?.let { usage ->
        AlertDialog(
            onDismissRequest = { pickingUsage = null },
            title = { Text("「${usage.displayName}」这个角色要用哪条") },
            text = {
                Column {
                    TextButton(onClick = {
                        ModelDispatchManager.setRoleOverride(context, roleId, usage, null)
                        pickingUsage = null
                        refreshTick++
                    }) { Text("跟随全局") }
                    providers.forEach { p ->
                        TextButton(onClick = {
                            ModelDispatchManager.setRoleOverride(context, roleId, usage, p.id)
                            pickingUsage = null
                            refreshTick++
                        }) { Text(p.name.ifBlank { "（未命名）" }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingUsage = null }) { Text("取消") }
            }
        )
    }
}
