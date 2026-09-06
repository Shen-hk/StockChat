package com.kuikly.stockchat.adapter

import android.graphics.Typeface
import com.tencent.kuikly.core.render.android.adapter.IKRFontAdapter
import com.kuikly.stockchat.KRApplication
import com.kuikly.stockchat.font.MiSansFont

object KRFontAdapter : IKRFontAdapter {

    override fun getTypeface(fontFamily: String, result: (Typeface?) -> Unit) {
        if (fontFamily.isEmpty()) {
            result(null) // 空值走系统默认，已被 MiSansFont 全局接管
        } else {
            var tfe: Typeface? = MiSansFont.typefaceFor(fontFamily)
            when (fontFamily) {
                "Qvideo Digit" -> {
                    tfe = Typeface.createFromAsset(
                        KRApplication.application.assets,
                        "fonts/$fontFamily.ttf"
                    )
                }
            }
            result(tfe)
        }
    }
}