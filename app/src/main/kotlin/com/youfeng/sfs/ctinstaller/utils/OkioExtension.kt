package com.youfeng.sfs.ctinstaller.utils

import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.buffer
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

fun Path.sha256(): String {
    val fileSystem = FileSystem.SYSTEM
    // 确保在使用 HashingSource 后正确关闭资源
    return fileSystem.source(this).use { fileSource ->
        HashingSource.sha256(fileSource).use { hashingSource ->
            hashingSource.buffer().use { bufferedSource ->
                // 读取文件内容以计算哈希值
                // 你需要确保整个文件都被读取，即使你不需要文件内容本身
                // HashingSource 在数据流过它时计算哈希
                val buffer = okio.Buffer() // 创建一个临时的 Buffer 来消耗数据
                while (bufferedSource.read(buffer, 8192L) != -1L) {
                    // 持续读取直到文件末尾
                }
                // 获取计算出的哈希值
                hashingSource.hash.hex()
            }
        }
    }
}