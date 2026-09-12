package com.kuikly.stockchat

import com.kuikly.stockchat.composer.AtCandidateProvider
import com.kuikly.stockchat.composer.CatalogEntry
import com.kuikly.stockchat.composer.CommandExecution
import com.kuikly.stockchat.composer.CommandInvocationParser
import com.kuikly.stockchat.composer.CommandInvocation
import com.kuikly.stockchat.composer.CommandRegistry
import com.kuikly.stockchat.composer.ComposerCatalog
import com.kuikly.stockchat.composer.ComposerTextOperations
import com.kuikly.stockchat.composer.MentionEntity
import com.kuikly.stockchat.composer.MentionType
import com.kuikly.stockchat.composer.SendPayload
import com.kuikly.stockchat.composer.SolidTokenRegistry
import com.kuikly.stockchat.composer.TriggerDetector
import com.kuikly.stockchat.composer.TriggerSession
import com.kuikly.stockchat.data.mock.MockQuoteProvider
import com.kuikly.stockchat.data.provider.QuoteRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerInteractionTest {
    @Test
    fun triggerDetectorOnlyStartsOnWordBoundary() {
        assertNotNull(TriggerDetector.detect("@茅台", 3))
        assertNotNull(TriggerDetector.detect("看一下 /复盘", 7))
        assertNull(TriggerDetector.detect("abc@163.com", 7))
        assertNull(TriggerDetector.detect("https://example.com", 8))
        assertNull(TriggerDetector.detect("@贵州 茅台", 4))
    }

    @Test
    fun atCandidatesRankWatchlistPinyinAndBoards() {
        val recommended = AtCandidateProvider.rank("")
        assertEquals("600519.SH", recommended.first().symbol)

        val pinyin = AtCandidateProvider.rank("gzmt")
        assertEquals("600519.SH", pinyin.first().symbol)

        val board = AtCandidateProvider.rank("白酒").first()
        assertEquals(MentionType.BOARD, board.entry.kind)
        assertEquals("BK0477", board.symbol)
    }

    @Test
    fun atCandidatesMergeRemoteSearchPoolEntries() {
        val remote = CatalogEntry("601127.SH", "赛力斯", "沪A", "", "sls")

        val merged = AtCandidateProvider.rank("赛力斯", extraEntries = listOf(remote))
        assertEquals("601127.SH", merged.first().symbol)
        assertEquals("搜索", merged.first().source)

        // 内置目录优先：远端池里与内置同 symbol 的条目不产生双份
        val deduped = AtCandidateProvider.rank("gzmt", extraEntries = listOf(remote.copy(symbol = "600519.SH")))
        assertEquals(1, deduped.count { it.symbol == "600519.SH" })
    }

    @Test
    fun slashCommandsResolveAliasesAndExecutionMode() {
        assertEquals("compare", CommandRegistry.resolve("vs")?.id)
        assertEquals(CommandExecution.LOCAL_ACTION, CommandRegistry.resolve("盯盘")?.execution)
        assertTrue(CommandRegistry.filter("复").any { it.id == "fupan" })
        assertTrue(CommandRegistry.suggest("请屏").any { it.id == "clear" })
    }

    @Test
    fun promptTemplateRendersHumanParameters() {
        val compare = CommandRegistry.resolve("对比")!!
        val prompt = CommandRegistry.renderPrompt(
            compare,
            mapOf("left" to "600519.SH 贵州茅台", "right" to "000858.SZ 五粮液", "dim" to "估值"),
        )

        assertTrue(prompt.contains("估值"))
        assertTrue(prompt.contains("600519.SH 贵州茅台"))
        assertTrue(prompt.contains("000858.SZ 五粮液"))
    }

    @Test
    fun commandParserBuildsTypedArgumentsOutsideThePageLayer() {
        val maotai = MentionEntity.of(ComposerCatalog.find("600519.SH")!!)
        val wuliangye = MentionEntity.of(ComposerCatalog.find("000858.SZ")!!)

        val invocation = CommandInvocationParser.parse(
            "/对比 @贵州茅台 @五粮液 估值",
            listOf(maotai, wuliangye),
            isExactSecurity = { false },
        )

        assertEquals("compare", invocation?.commandId)
        assertEquals(
            mapOf("left" to "贵州茅台", "right" to "五粮液", "dim" to "估值"),
            invocation?.args,
        )
        assertTrue(CommandInvocationParser.missingRequiredParams(CommandRegistry.resolve("对比")!!, invocation!!.args).isEmpty())
    }

    @Test
    fun commandParserFallsBackToExactPlainTextSecurity() {
        val invocation = CommandInvocationParser.parse(
            "/解读 600519.SH",
            emptyList(),
            isExactSecurity = { it == "600519.SH" },
        )

        assertEquals(mapOf("target" to "600519.SH"), invocation?.args)
        assertEquals("600519.SH", CommandInvocationParser.currentParameterQuery("/解读 600519.SH", "解读"))
        assertEquals("贵州茅台怎么看", CommandInvocationParser.remainder("/ssq 贵州茅台怎么看", "深水区"))
    }

    @Test
    fun triggerInsertionPreservesCursorAndReplacesAnActiveFragment() {
        val inserted = ComposerTextOperations.insertTrigger("看看贵州茅台", 2, null, '@')
        assertEquals("看看 @贵州茅台", inserted.text)
        assertEquals(4, inserted.cursor)

        val replaced = ComposerTextOperations.insertTrigger(
            text = "看看 @茅台",
            cursor = 6,
            activeSession = TriggerSession('@', 3, "茅台", 6),
            trigger = '/',
        )
        assertEquals("看看 /", replaced.text)
        assertEquals(4, replaced.cursor)
    }

    @Test
    fun triggerRemovalClearsOnlyTheUnfinishedFragment() {
        val removed = ComposerTextOperations.removeTriggerFragment(
            text = "看看 @茅台 怎么样",
            cursor = 6,
            activeSession = TriggerSession('@', 3, "茅台", 6),
        )

        assertEquals("看看  怎么样", removed.text)
        assertEquals(3, removed.cursor)
    }

    @Test
    fun sendPayloadInjectsMentionCommandAndBoardContext() {
        val board = MentionEntity("BK0477", "白酒", MentionType.BOARD, "@白酒(板块)")
        val payload = SendPayload(
            text = "@白酒(板块) 怎么看",
            mentions = listOf(board),
            command = CommandInvocation("fupan", "复盘", mapOf("period" to "周")),
            renderedPrompt = "请对白酒做周线级别复盘。",
            contextNotes = listOf("深水区模式"),
        )
        val note = payload.systemNote().orEmpty()

        assertTrue(note.contains("BK0477 白酒"))
        assertTrue(note.contains("600519.SH 贵州茅台"))
        assertTrue(note.contains("000858.SZ 五粮液"))
        assertTrue(note.contains("深水区模式"))
    }

    @Test
    fun solidTokensAreDeduplicatedAndDroppedAfterTextEdit() {
        val maotai = MentionEntity.of(ComposerCatalog.find("600519.SH")!!)
        val wuliangye = MentionEntity.of(ComposerCatalog.find("000858.SZ")!!)
        val verified = SolidTokenRegistry.verify(
            listOf(maotai, maotai, wuliangye),
            "@贵州茅台 和 五粮液",
        )

        assertEquals(listOf("600519.SH"), verified.map { it.symbol })
    }

    @Test
    fun quoteRepositoryExposesRankedLocalSearch() {
        val repository = QuoteRepository(MockQuoteProvider())

        assertEquals("600519.SH", repository.search("茅台").first().symbol)
        assertEquals("000001.SZ", repository.search("平安", limit = 1).single().symbol)
        assertTrue(repository.search("").isNotEmpty())
    }
}
