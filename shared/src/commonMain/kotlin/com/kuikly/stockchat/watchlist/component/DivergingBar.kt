package com.kuikly.stockchat.watchlist.component

import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.View

/**
 * 中心分界比例条（doc 24 §7 DivergingBar）。
 *
 * 涨跌家数是「对比一个共同基准（全体）」的关系型数据，列表答不了
 * 「红绿盘哪边多、多多少」——中线固定居中，涨向左生长、跌向右生长，
 * 谁的段更长一眼可见，这正是它比「涨 3 · 平 1 · 跌 4」文案多出来的信息。
 *
 * 布局：左半区右对齐涨段，1px 中线，右半区左对齐跌段；段用 flex 权重占
 * 半区的份额（width() 是 dp 不是百分比），空白留透明——全涨时右侧整段
 * 空置，缺口本身也是结论。
 *
 * @param rising   上涨家数。
 * @param flat     平盘家数（不计入段宽，只进文案）。
 * @param falling  下跌家数。
 */
fun ViewContainer<*, *>.DivergingBar(
    theme: StockChatTheme,
    rising: Int,
    flat: Int,
    falling: Int,
) {
    val total = rising + flat + falling
    if (total <= 0) return
    val risingFill = rising.toFloat() / total
    val fallingFill = falling.toFloat() / total
    View {
        attr {
            height(6f)
            flexDirectionRow()
            alignItemsCenter()
        }
        // 左半区：涨段右对齐（贴中线生长），份额 = flex 权重
        View {
            attr {
                flex(1f)
                height(6f)
                flexDirectionRow()
                justifyContentFlexEnd()
                alignItemsCenter()
            }
            View {
                attr {
                    flex(risingFill)
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(theme.rise)
                }
            }
        }
        // 中线：固定居中
        View {
            attr {
                width(2f)
                height(6f)
                marginLeft(1f)
                marginRight(1f)
                borderRadius(1f)
                backgroundColor(theme.divider)
            }
        }
        // 右半区：跌段左对齐（贴中线生长）
        View {
            attr {
                flex(1f)
                height(6f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    flex(fallingFill)
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(theme.fall)
                }
            }
        }
    }
}
