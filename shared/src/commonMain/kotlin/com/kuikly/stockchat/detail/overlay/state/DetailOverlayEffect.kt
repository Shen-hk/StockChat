package com.kuikly.stockchat.detail.overlay.state

/**
 * Detail 页 overlay 仲裁域占位 sealed（docs/43 D4：与 D3 同——本域为终端消费者，
 * 暂无向下游发出的副作用需求；保留 sealed interface 是为保持 state 四件套
 * 范式一致与未来扩展位）。
 */
internal sealed interface DetailOverlayEffect