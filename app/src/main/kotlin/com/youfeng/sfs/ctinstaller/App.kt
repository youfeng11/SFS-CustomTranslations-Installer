package com.youfeng.sfs.ctinstaller

import android.app.Application
import android.os.Build
import timber.log.Timber
import dagger.hilt.android.HiltAndroidApp
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.timber.FileLoggingTree
import rikka.sui.Sui
import java.time.ZonedDateTime
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var fileLoggingTree: FileLoggingTree
    
    // Hilt 初始化完成后，执行依赖于注入对象的逻辑
    @Inject
    fun initializeApp() {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        
        // 🎯 步骤 1: 迁移 Shell 初始化逻辑 (使用 runBlocking 获取 Flow 的初始值)
        runBlocking {
            val command = settingsRepository.userSettings.first().customSuCommand
            val builder = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)

            if (command.isNotEmpty()) {
                builder.setCommands(command)
            }

            Shell.setDefaultBuilder(builder)
        }
        
        // 🎯 步骤 2: 迁移所有初始化日志记录
        Timber.plant(FileLoggingTree(this))
        Timber.i("应用初始化完成") // 更改日志名称以反映其生命周期
        Timber.i("应用版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        Timber.i("设备信息：${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL} ${Build.VERSION.SDK_INT}")
        
        val timeZoneInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "${ZonedDateTime.now().zone.id} UTC${ZonedDateTime.now().offset}"
        } else {
            TimeZone.getDefault().id
        }
        Timber.i("时区：$timeZoneInfo")
    }
    
    override fun onCreate() {
        super.onCreate()
        Sui.init(packageName)
    }
}
