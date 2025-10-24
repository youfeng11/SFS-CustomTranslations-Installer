package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import com.youfeng.sfs.ctinstaller.utils.md5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val client = OkHttpClient()

    suspend fun fetchContentFromUrl(url: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                // 1️⃣ 获取 UTF-8 解码后的文件名
                val rawFileName = response.header("Content-Disposition")
                    ?.let { parseFileNameFromDisposition(it) }
                    ?: url.substringAfterLast('/').ifBlank { "未命名语言.txt" }

                // 🔤 确保任何来源的文件名都被 UTF-8 解码
                val fileName = try {
                    URLDecoder.decode(rawFileName, "UTF-8")
                } catch (_: Exception) {
                    rawFileName // 解码失败就用原值
                }

                response.body.string() to fileName
            }
        }

    /**
     * 下载文件到缓存目录，自动解析 UTF-8 编码文件名。
     */
    suspend fun downloadFileToCache(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            // 1️⃣ 获取 UTF-8 解码后的文件名
            val rawFileName = response.header("Content-Disposition")
                ?.let { parseFileNameFromDisposition(it) }
                ?: url.substringAfterLast('/').ifBlank { "${url.md5()}.txt" }

            // 🔤 确保任何来源的文件名都被 UTF-8 解码
            val fileName = try {
                URLDecoder.decode(rawFileName, "UTF-8")
            } catch (_: Exception) {
                rawFileName // 解码失败就用原值
            }

            // 2️⃣ 目标路径
            val cacheDir = context.externalCacheDir
                ?: throw IOException("External cache directory not available")
            val targetPath = "${cacheDir.absolutePath}/$fileName".toPath()

            // 3️⃣ 写入文件（纯 Okio）
            val source = response.body.source()

            FileSystem.SYSTEM.sink(targetPath).buffer().use { sink ->
                sink.writeAll(source)
            }

            targetPath.toString()
        }
    }

    /**
     * 从 Content-Disposition 中提取 UTF-8 或普通文件名。
     */
    private fun parseFileNameFromDisposition(header: String): String? {
        // filename*=UTF-8''encoded
        val utf8Match = Regex("filename\\*=(?:UTF-8'')?([^;]+)").find(header)
        if (utf8Match != null) {
            val encodedName = utf8Match.groupValues[1]
            return URLDecoder.decode(encodedName, "UTF-8")
        }

        // 普通 filename="..."，也可能是 URL 编码
        val simpleMatch = Regex("filename=\"?([^\";]+)\"?").find(header)
        val name = simpleMatch?.groupValues?.getOrNull(1)
        return name?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (_: Exception) {
                it
            }
        }
    }
}
