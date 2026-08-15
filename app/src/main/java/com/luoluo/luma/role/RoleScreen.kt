package com.luoluo.luma.role

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
import androidx.compose.material3.Button
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

/**
 * 角色管理界面：建新角色、切换、删除。
 *
 * 切换/删除之后调onDone()，交给调用方（LumaApp）决定接下来怎么办——
 * 这个界面本身不关心"切完之后聊天记录怎么换"，那是ChatScreen那边的事
 * (靠给ChatViewModel换一个新的roleId让它重新读数据库，见ChatScreen.kt)。
 */
@Composable
fun RoleScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var roles by remember { mutableStateOf(RoleManager.getRoles(context)) }
    var activeId by remember { mutableStateOf(RoleManager.getActiveRoleId(context)) }
    var newRoleName by remember { mutableStateOf("") }

    fun refresh() {
        roles = RoleManager.getRoles(context)
        activeId = RoleManager.getActiveRoleId(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("角色管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("返回聊天") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(roles) { role ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(role.name, style = MaterialTheme.typography.bodyLarge)
                        if (role.id == activeId) {
                            Text("当前使用中", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (role.id != activeId) {
                        TextButton(onClick = {
                            RoleManager.setActiveRoleId(context, role.id)
                            refresh()
                            onDone()
                        }) { Text("切换") }
                    }
                    TextButton(onClick = {
                        RoleManager.deleteRole(context, role.id)
                        refresh()
                    }) { Text("删除") }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newRoleName,
                onValueChange = { newRoleName = it },
                label = { Text("新角色名字") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val name = newRoleName.trim()
                if (name.isNotEmpty()) {
                    RoleManager.createRole(context, name)
                    newRoleName = ""
                    refresh()
                }
            }) {
                Text("新建")
            }
        }
    }
}
