package com.kuikly.stockchat

import android.app.Application
import com.kuikly.stockchat.font.MiSansFont

class KRApplication : Application() {

    init {
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        MiSansFont.apply(this)
    }

    companion object {
        lateinit var application: Application
    }
}