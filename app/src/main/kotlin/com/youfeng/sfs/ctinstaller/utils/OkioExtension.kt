package com.youfeng.sfs.ctinstaller.utils

import okio.FileSystem
import okio.Path
import java.io.IOException

fun Path.isDirectoryExists(): Boolean {
    val fileSystem = FileSystem.SYSTEM
    return try {
        fileSystem.exists(this) && fileSystem.metadata(this).isDirectory
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

