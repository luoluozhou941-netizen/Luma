package com.luoluo.luma.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * 1g：用户档案编辑界面。就一个名字字段，故意保持简单——
 * 不写提示词、不做人设，纯粹是"AI知道跟自己说话的人叫什么"这一件事。
 */
@Composable
fun UserProfileScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(UserProfileManager.getProfile(context).name) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("我是谁", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("返回聊天") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "填了名字，AI才知道跟自己说话的是谁。不填也没关系，就是不知道你叫什么。",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名字") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                UserProfileManager.updateName(context, name.trim())
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
    }
}