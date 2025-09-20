package com.youfeng.sfs.ctinstaller.service

import android.util.Log
import java.io.File
import kotlin.system.exitProcess

class FileService : IFileService.Stub() {

    override fun copyFile(srcPath: String, destPath: String) {
        val src = File(srcPath)
        val dest = File(destPath)
        src.copyTo(dest, overwrite = true)
    }

    override fun isExists(path: String): Boolean =
        File(path).exists()

    override fun isDirectory(path: String): Boolean =
        File(path).isDirectory

    override fun mkdirs(path: String) {
        File(path).mkdirs()
    }

    override fun destroy() {
        Log.i("FileService", "destroy")
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }
}