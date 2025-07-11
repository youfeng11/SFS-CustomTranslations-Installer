package com.youfeng.sfs.ctinstaller

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import rikka.sui.Sui

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Sui.init(packageName)
    }
}
