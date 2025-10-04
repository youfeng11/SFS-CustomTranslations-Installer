package com.youfeng.sfs.ctinstaller.service

import android.util.Log
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

class ShizukuFileService : IShizukuFileService.Stub() {

    private val fs = FileSystem.SYSTEM

    override fun copyFile(srcPath: String, destPath: String) {
        val src = srcPath.toPath()
        val dest = destPath.toPath()

        fs.copy(src, dest)
    }

    override fun isExists(path: String): Boolean =
        fs.metadataOrNull(path.toPath()) != null

    override fun isDirectory(path: String): Boolean =
        fs.metadataOrNull(path.toPath())?.isDirectory == true

    override fun mkdirs(path: String) {
        fs.createDirectories(path.toPath())
    }

    override fun destroy() {
        Log.i("FileService", "destroy")
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }
}
