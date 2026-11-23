package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        // Always bright light mode for elderly-friendly readability
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        MmkvManager.initialize(this)
        SettingsManager.initApp(this)
    }
}
