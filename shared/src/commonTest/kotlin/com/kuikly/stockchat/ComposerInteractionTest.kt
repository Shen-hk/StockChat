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
        assertNotNull(TriggerDetector.detect("看一下 /盯盘", 7))
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
        assertEquals("monitor", CommandRegistry.resolve("dp")?.id)
        assertEquals(CommandExecution.LOCAL_ACTION, CommandRegistry.resolve("盯盘")?.execution)
        assertEquals(setOf("monitor", "clear"), CommandRegistry.all.map { it.id }.toSet())
        assertTrue(CommandRegistry.filter("盯").any { it.id == "monitor" })
        assertTrue(CommandRegistry.suggest("请屏").any { it.id == "clear" })
    }

    @Test
    fun commandParserBuildsTypedArgumentsOutsideThePageLayer() {
        val maotai = MentionEntity.of(ComposerCatalog.find("600519.SH")!!)

        val invocation = CommandInvocationParser.parse(
            "/盯盘 @贵州茅台",
            listOf(maotai),
            isExactSecurity = { false },
        )

        assertEquals("monitor", invocation?.commandId)
        assertEquals(mapOf("target" to "贵州茅台"), invocation?.args)
        assertTrue(CommandInvocationParser.missingRequiredParams(CommandRegistry.resolve("盯盘")!!, invocation!!.args).isEmpty())

        val noTarget = CommandInvocationParser.parse("/盯盘", emptyList()) { false }
        assertEquals(mapOf("target" to ""), noTarget?.args)
        assertTrue(CommandInvocationParser.missingRequiredParams(CommandRegistry.resolve("盯盘")!!, noTarget!!.args).isEmpty())
    }

    @Test
    fun commandParserFallsBackToExactPlainTextSecurity() {
        val invocation = CommandInvocationParser.parse(
            "/盯盘 600519.SH",
            emptyList(),
            isExactSecurity = { it == "600519.SH" },
        )

        assertEquals(mapOf("target" to "600519.SH"), invocation?.args)
        assertEquals("600519.SH", CommandInvocationParser.currentParameterQuery("/盯盘 600519.SH", "盯盘"))
        assertEquals("600519.SH", CommandInvocationParser.remainder("/dp 600519.SH", "盯盘"))
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
            command = CommandInvocation("monitor", "盯盘", mapOf("target" to "白酒")),
            renderedPrompt = "白酒",
            contextNotes = emptyList(),
        )
        val note = payload.systemNote().orEmpty()

        assertTrue(note.contains("BK0477 白酒"))
        assertTrue(note.contains("600519.SH 贵州茅台"))
        assertTrue(note.contains("000858.SZ 五粮液"))
        assertTrue(note.contains("盯盘"))
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
