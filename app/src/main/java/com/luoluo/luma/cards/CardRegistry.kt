package com.luoluo.luma.cards

import android.content.Context
import androidx.compose.runtime.Composable
import com.luoluo.luma.cards.testcard.TEST_CARD_MANIFEST
import com.luoluo.luma.cards.testcard.TestCardScreen
import com.luoluo.luma.host.CardHostApi
import com.luoluo.luma.storage.CardKeyValueEntity
import com.luoluo.luma.storage.RoleDatabaseManager

/**
 * 卡片管理器：所有卡片"编译时内置，运行时开关"这条规则的具体落地点。
 *
 * 注意这个文件本身允许直接依赖storage包——"卡片跟底座内部实现不能有直接依赖"这条规则
 * 约束的是具体的卡片实现（比如TestCardScreen），不包括这个管理器本身。
 * 管理器就是底座里"卡片管理器"这个组件，它当然要能碰到存储层。
 *
 * 开关状态是真正的运行时开关：存在**当前角色自己的**数据库里，界面上有Switch可以直接切，
 * 不用改代码重新编译。manifest里的enabled字段变成了"默认值"——
 * 第一次装机、还没手动切换过的时候用这个值，切过一次之后就以存储里的值为准。
 * 这个开关是按角色分别存的：A角色关掉了健康卡片，不影响B角色那边这张卡片是不是开着。
 *
 * 新增一张卡片的步骤：
 * 1. 在cards/下建一个新文件夹（比如cards/health/），写自己的manifest + 界面Composable，
 *    界面里只能依赖CardHostApi接口，不能直接import其他卡片或者storage/chat包里的类
 * 2. 把新卡片的manifest加进下面的allManifests列表
 * 3. 在RenderCard()里加一个when分支，指向新卡片的Composable
 */
object CardRegistry {

    private val allManifests: List<CardManifest> = listOf(
        TEST_CARD_MANIFEST
    )

    fun getAllManifests(): List<CardManifest> = allManifests

    private fun enabledKey(cardId: String) = "card-manager:enabled:$cardId"

    /** 读这张卡片在当前角色下是开还是关——先看这个角色有没有手动切换过，没有就用manifest里的默认值 */
    suspend fun isCardEnabled(context: Context, roleId: String, manifest: CardManifest): Boolean {
        val dao = RoleDatabaseManager.getDatabase(context, roleId).cardKeyValueDao()
        val stored = dao.get(enabledKey(manifest.id))?.value
        return stored?.toBooleanStrictOrNull() ?: manifest.enabled
    }

    /** 界面上的开关调这个，只影响当前这个角色，立刻生效，不用重新编译 */
    suspend fun setCardEnabled(context: Context, roleId: String, cardId: String, enabled: Boolean) {
        val dao = RoleDatabaseManager.getDatabase(context, roleId).cardKeyValueDao()
        dao.set(CardKeyValueEntity(key = enabledKey(cardId), value = enabled.toString()))
    }

    @Composable
    fun RenderCard(manifest: CardManifest, hostApi: CardHostApi) {
        when (manifest.id) {
            TEST_CARD_MANIFEST.id -> TestCardScreen(hostApi)
            // 以后新卡片在这里加一行：xxx.id -> XxxScreen(hostApi)
        }
    }
}
