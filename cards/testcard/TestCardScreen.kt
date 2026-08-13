package com.luoluo.luma.cards.testcard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luoluo.luma.host.CardHostApi
import kotlinx.coroutines.launch

private const val STORAGE_KEY = "test-card:count"

/**
 * 骨架验证用的卡片：点一下按钮，计数加一，存进底座给的存储里；
 * 杀掉app重开，数字还在，就说明这张卡片真的通过CardHostApi接口摸到了底座的存储能力，
 * 而不是自己另起炉灶存了点什么在内存里做样子。
 *
 * 这个Composable只依赖CardHostApi这个接口，没有import任何storage包或chat包里的类——
 * 这就是"卡片跟底座之间只走接口，不碰内部实现"这条规则的实际样子。
 */
@Composable
fun TestCardScreen(hostApi: CardHostApi) {
    var count by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        hostApi.getValue(STORAGE_KEY)?.toIntOrNull()?.let { count = it }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("测试卡片", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("点了 $count 次（关app重开数字还在，说明真的存进去了）")
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = {
                    count += 1
                    scope.launch {
                        hostApi.setValue(STORAGE_KEY, count.toString())
                    }
                }) {
                    Text("点我")
                }
            }
        }
    }
}
