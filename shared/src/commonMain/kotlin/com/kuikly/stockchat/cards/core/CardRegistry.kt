package com.kuikly.stockchat.cards.core

import com.tencent.kuikly.core.base.ViewContainer

interface CardRenderer<M : CardModel> {
    val cardType: String
    fun render(container: ViewContainer<*, *>, model: M, context: CardContext)
}

object CardRegistry {
    private val renderers = mutableMapOf<String, CardRenderer<out CardModel>>()

    fun register(renderer: CardRenderer<out CardModel>) {
        renderers[renderer.cardType] = renderer
    }

    fun dispatch(type: String): CardRenderer<out CardModel>? = renderers[type]

    fun registeredTypes(): List<String> = renderers.keys.sorted()

    fun clear() = renderers.clear()
}
