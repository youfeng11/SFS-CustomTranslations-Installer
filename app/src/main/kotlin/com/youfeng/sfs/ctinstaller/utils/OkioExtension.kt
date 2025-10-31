package com.youfeng.sfs.ctinstaller.utils

import android.util.Log
import com.youfeng.sfs.ctinstaller.core.TAG
import okio.FileSystem
import okio.Path
import java.io.IOException

fun Path.isDirectoryExists(): Boolean {
    val fileSystem = FileSystem.SYSTEM
    return try {
        fileSystem.exists(this) && fileSystem.metadata(this).isDirectory
    } catch (e: IOException) {
        Log.i(TAG, "文件不存在或非文件夹", e)
        false
    }
}
