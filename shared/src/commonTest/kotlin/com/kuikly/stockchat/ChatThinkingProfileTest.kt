package com.kuikly.stockchat

import com.kuikly.stockchat.chat.ChatThinkingProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 思维画像纯函数（doc 32 §6.3）：六环关键词归档，宁松勿严。 */
class ChatThinkingProfileTest {

    @Test
    fun emptyMessagesAllMissing() {
        val hits = ChatThinkingProfile.analyze(emptyList())
        assertEquals(ChatThinkingProfile.STAGES.size, hits.size)
        assertTrue(hits.none { it.covered })
        assertEquals(0, ChatThinkingProfile.coveredCount(hits))
        assertEquals("明确标的、行业与赛道、基本面、走势与技术、事件与催化、风险与仓位", ChatThinkingProfile.missingTitles(hits))
    }

    @Test
    fun keywordHitsCoverStage() {
        val hits = ChatThinkingProfile.analyze(listOf("帮我看看宁德时代的财报，营收和利润怎么样？"))
        val fundamental = hits.first { it.stage.key == "fundamental" }
        assertTrue(fundamental.covered)
        assertEquals(1, fundamental.count)
        assertTrue(fundamental.example.contains("财报"))
        // 无关环不被误点亮
        assertFalse(hits.first { it.stage.key == "risk" }.covered)
    }

    @Test
    fun countAccumulatesAcrossMessages() {
        val hits = ChatThinkingProfile.analyze(
            listOf("新易盛解禁那天要注意吗", "最近的日历里还有什么事件"),
        )
        assertEquals(2, hits.first { it.stage.key == "event" }.count)
    }

    @Test
    fun cardProtocolStrippedFromExample() {
        val hits = ChatThinkingProfile.analyze(listOf("```card:quote\n{}\n``` K线趋势如何"))
        val technique = hits.first { it.stage.key == "technique" }
        assertTrue(technique.covered)
        assertFalse(technique.example.contains("card:"))
    }

    @Test
    fun missingTitlesExcludesCoveredStage() {
        val hits = ChatThinkingProfile.analyze(listOf("我的自选为什么跌", "波动是不是太大了"))
        assertTrue(hits.first { it.stage.key == "target" }.covered)
        assertTrue(hits.first { it.stage.key == "risk" }.covered)
        val missing = ChatThinkingProfile.missingTitles(hits)
        assertFalse(missing.contains("风险与仓位"))
        assertTrue(missing.contains("基本面"))
    }
}
