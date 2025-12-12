package com.youfeng.sfs.ctinstaller.data.local

import android.os.Environment

object SfsFileConfig {
    // SFS 游戏的包名
    const val PACKAGE_NAME = "com.StefMorojna.SpaceflightSimulator"

    // 基础目录结构常量
    const val ANDROID_DATA_DIRECTORY = "Android/data"

    // 这里使用 getter 而不是直接赋值，更加安全，虽然 Environment 在 Object 初始化时调用通常也没问题
    val externalStoragePath: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    // SFS 数据目录路径
    val dataDirectoryPath: String
        get() = "$externalStoragePath/$ANDROID_DATA_DIRECTORY/$PACKAGE_NAME"

    // 自定义翻译存放路径
    val customTranslationDirectoryPath: String
        get() = "$dataDirectoryPath/files/Custom Translations/"
}