package com.kuikly.stockchat.cards.core

import com.tencent.kuikly.core.base.ViewContainer

interface CardRenderer {
    val cardType: String
    fun render(container: ViewContainer<*, *>, model: CardModel, context: CardContext)
}

object CardRegistry {
    private val renderers = mutableMapOf<String, CardRenderer>()

    fun register(renderer: CardRenderer) {
        renderers[renderer.cardType] = renderer
    }

    fun dispatch(type: String): CardRenderer? = renderers[type]

    fun registeredTypes(): List<String> = renderers.keys.sorted()

    fun clear() = renderers.clear()
}
