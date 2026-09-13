package com.kuikly.stockchat

import com.kuikly.stockchat.data.provider.DisclosureItem
import com.kuikly.stockchat.data.provider.DisclosureProvider
import com.kuikly.stockchat.data.provider.FundFlow
import com.kuikly.stockchat.data.provider.FundFlowProvider
import com.kuikly.stockchat.data.provider.FundamentalBundle
import com.kuikly.stockchat.data.provider.FundamentalProvider
import com.kuikly.stockchat.data.provider.HotspotSnapshot
import com.kuikly.stockchat.data.provider.MarketCalendarEvent
import com.kuikly.stockchat.data.provider.MarketInsightRepository
import com.kuikly.stockchat.data.provider.MarketOverview
import com.kuikly.stockchat.data.provider.MarketOverviewProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketInsightRepositoryTest {
    @Test
    fun publishesUsableMarketFallbackWhenNetworkNeverCallsBack() {
        val repository = MarketInsightRepository(
            onlineFundFlow = object : FundFlowProvider {
                override fun fundFlow(symbol: String, onResult: (FundFlow?) -> Unit) = Unit
            },
            onlineFundamentals = object : FundamentalProvider {
                override fun fundamentals(symbol: String, onResult: (FundamentalBundle?) -> Unit) = Unit
                override fun calendar(onResult: (List<MarketCalendarEvent>) -> Unit) = Unit
            },
            onlineDisclosures = object : DisclosureProvider {
                override fun disclosures(symbol: String, onResult: (List<DisclosureItem>) -> Unit) = Unit
            },
            onlineMarket = object : MarketOverviewProvider {
                override fun overview(onResult: (MarketOverview?) -> Unit) = Unit
                override fun hotspots(onResult: (HotspotSnapshot?) -> Unit) = Unit
            },
        )

        var overview: MarketOverview? = null
        var hotspots: HotspotSnapshot? = null
        repository.loadOverview { overview = it }
        repository.loadHotspots { hotspots = it }

        assertTrue(overview!!.indices.isNotEmpty())
        assertTrue(overview!!.sectors.isNotEmpty())
        assertTrue(hotspots!!.limitUps.size >= 6)
        assertEquals("演示数据", hotspots!!.stamp.tier.label)
    }
}
