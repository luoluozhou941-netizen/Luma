package com.luoluo.luma.memory

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
import com.luoluo.luma.role.RoleManager

/**
 * 1g：记忆管理界面。显示"这个角色能看到的"所有卡片（自己的+用户的+跟自己的关系），
 * 不是"全部卡片"——别的角色的私有记忆不会出现在这里，跟MemoryManager.getVisibleCards
 * 的过滤规则保持一致。
 */
@Composable
fun MemoryScreen(roleId: String, roleName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var cards by remember { mutableStateOf(MemoryManager.getVisibleCards(context, roleId)) }
    var editingCard by remember { mutableStateOf<MemoryCard?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        cards = MemoryManager.getVisibleCards(context, roleId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("「$roleName」的记忆", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("返回") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "这里显示这个角色能看到的记忆：它自己的、用户的、还有跟它的关系记忆",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(cards) { card ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            ownerLabel(card, roleName) + " · " + if (card.drawer == Drawer.ALWAYS) "一直带着" else "手动翻",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = { editingCard = card }) { Text("编辑") }
                    TextButton(onClick = {
                        MemoryManager.deleteCard(context, card.id)
                        refresh()
                    }) { Text("删除") }
                }
            }
        }

        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("新增记忆")
        }
    }

    if (showAddDialog) {
        MemoryEditDialog(
            initial = null,
            currentRoleId = roleId,
            onDismiss = { showAddDialog = false },
            onSave = { title, content, ownerType, ownerRoleId, drawer ->
                MemoryManager.addCard(context, title, content, ownerType, ownerRoleId, drawer)
                refresh()
                showAddDialog = false
            }
        )
    }

    editingCard?.let { card ->
        MemoryEditDialog(
            initial = card,
            currentRoleId = roleId,
            onDismiss = { editingCard = null },
            onSave = { title, content, ownerType, ownerRoleId, drawer ->
                MemoryManager.updateCard(
                    context,
                    card.copy(title = title, content = content, ownerType = ownerType, ownerRoleId = ownerRoleId, drawer = drawer)
                )
                refresh()
                editingCard = null
            }
        )
    }
}

private fun ownerLabel(card: MemoryCard, currentRoleName: String): String = when (card.ownerType) {
    OwnerType.USER -> "用户的（所有角色都能看到）"
    OwnerType.ROLE -> "$currentRoleName 自己的"
    OwnerType.RELATIONSHIP -> "跟 $currentRoleName 的关系"
}

/**
 * 新增/编辑记忆卡片共用的弹窗。owner选"关系"的时候，下面会多出一个角色下拉——
 * 现在就两个角色，以后加了新角色下拉自动多一个选项，不用改这里的逻辑。
 */
@Composable
private fun MemoryEditDialog(
    initial: MemoryCard?,
    currentRoleId: String,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, ownerType: OwnerType, ownerRoleId: String?, drawer: Drawer) -> Unit
) {
    val context = LocalContext.current
    val allRoles = remember { RoleManager.getRoles(context) }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var ownerType by remember { mutableStateOf(initial?.ownerType ?: OwnerType.USER) }
    // 关系类型要选跟哪个角色的关系，默认选当前正在看的这个角色
    var relationRoleId by remember {
        mutableStateOf(if (initial?.ownerType == OwnerType.RELATIONSHIP) initial.ownerRoleId else currentRoleId)
    }
    var drawer by remember { mutableStateOf(initial?.drawer ?: Drawer.COLD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增记忆" else "编辑记忆") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("归属", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    FilterChip(
                        selected = ownerType == OwnerType.USER,
                        onClick = { ownerType = OwnerType.USER },
                        label = { Text("用户") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = ownerType == OwnerType.ROLE,
                        onClick = { ownerType = OwnerType.ROLE },
                        label = { Text("这个角色") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = ownerType == OwnerType.RELATIONSHIP,
                        onClick = { ownerType = OwnerType.RELATIONSHIP },
                        label = { Text("关系") }
                    )
                }

                if (ownerType == OwnerType.RELATIONSHIP) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("跟哪个角色的关系", style = MaterialTheme.typography.labelMedium)
                    Row {
                        allRoles.forEach { role ->
                            FilterChip(
                                selected = relationRoleId == role.id,
                                onClick = { relationRoleId = role.id },
                                label = { Text(role.name) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("要不要一直带着", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    FilterChip(
                        selected = drawer == Drawer.ALWAYS,
                        onClick = { drawer = Drawer.ALWAYS },
                        label = { Text("一直带着") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = drawer == Drawer.COLD,
                        onClick = { drawer = Drawer.COLD },
                        label = { Text("手动翻") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    val finalOwnerRoleId = when (ownerType) {
                        OwnerType.USER -> null
                        OwnerType.ROLE -> currentRoleId
                        OwnerType.RELATIONSHIP -> relationRoleId
                    }
                    onSave(title.trim(), content.trim(), ownerType, finalOwnerRoleId, drawer)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}