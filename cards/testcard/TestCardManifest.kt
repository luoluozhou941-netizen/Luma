package com.luoluo.luma.cards.testcard

import com.luoluo.luma.cards.CardManifest
import com.luoluo.luma.cards.CardType

/**
 * 想临时禁用这张卡片，不用改这里的enabled重新编译——
 * 直接在app里"卡片管理"那个区域点开关就行，是真正的运行时开关（存在数据库里）。
 * 下面的enabled只是"第一次装机、还没手动切换过之前"用的默认值。
 */
val TEST_CARD_MANIFEST = CardManifest(
    id = "test-card",
    name = "测试卡片",
    type = CardType.DISPLAY,
    enabled = true
)
