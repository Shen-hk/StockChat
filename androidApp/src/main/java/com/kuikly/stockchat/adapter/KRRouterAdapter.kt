package com.kuikly.stockchat.adapter

import android.app.Activity
import android.content.Context
import com.tencent.kuikly.core.render.android.adapter.IKRRouterAdapter
import com.kuikly.stockchat.KuiklyRenderActivity
import com.kuikly.stockchat.R
import org.json.JSONObject

object KRRouterAdapter : IKRRouterAdapter {

    override fun openPage(
        context: Context,
        pageName: String,
        pageData: JSONObject,
    ) {
        KuiklyRenderActivity.start(context, pageName, pageData)
        // 容器变换交接（灵动岛下拉 → 详情）：发送页在卡片形变 ~90% 处就
        // 触发路由，这里用整页淡入替换默认平移转场，详情页与形变收尾
        // 重叠渐显；exit=0 让发送页保持全屏玻璃帧作淡入的底。
        if (pageData.optString("krTransition") == "islandExpand") {
            (context as? Activity)?.overridePendingTransition(R.anim.kr_handoff_fade_in, 0)
        }
    }

    override fun closePage(context: Context) {
        (context as? Activity)?.finish()
    }
}