package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.utils.md5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val client = OkHttpClient()

    /**
     * 从 URL 获取内容和 UTF-8 解码后的文件名。
     */
    suspend fun fetchContentFromUrl(url: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(context.getString(R.string.error_unexpected_code, response))
                }

                // 1️⃣ 优先从 Content-Disposition 获取已解码的文件名。
                val fileName = response.header("Content-Disposition")
                    ?.let { parseFileNameFromDisposition(it) }
                    // 2️⃣ 否则，使用 URL 路径的最后一段，并进行一次 URL 解码。
                    ?: url.substringAfterLast('/')
                        .ifBlank { context.getString(R.string.unnamed_translation_file_name) + ".txt" }
                        .let { rawName ->
                            try {
                                // URL 路径中的文件名是 URL 编码的
                                URLDecoder.decode(rawName, "UTF-8")
                            } catch (_: Exception) {
                                rawName
                            }
                        }

                response.body.string() to fileName
            }
        }

    /**
     * 下载文件到缓存目录，自动解析文件名。
     */
    suspend fun downloadFileToCache(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.error_unexpected_code, response))
            }

            // 1️⃣ 优先从 Content-Disposition 获取已解码的文件名。
            val fileName = response.header("Content-Disposition")
                ?.let { parseFileNameFromDisposition(it) }
                // 2️⃣ 否则，使用 URL 路径的最后一段（使用 MD5 作为后备名，避免重复）
                ?: url.substringAfterLast('/')
                    .ifBlank { "${url.md5()}.txt" }
                    .let { rawName ->
                        try {
                            // URL 路径中的文件名是 URL 编码的
                            URLDecoder.decode(rawName, "UTF-8")
                        } catch (_: Exception) {
                            rawName
                        }
                    }

            // 3️⃣ 目标路径
            val cacheDir = context.externalCacheDir
                ?: throw IOException(context.getString(R.string.error_external_cache_directory_not_available))
            // 注意：此时 fileName 已经是完全解码且可用的文件名
            val targetPath = File(cacheDir, fileName).absolutePath.toPath()

            // 4️⃣ 写入文件（纯 Okio）
            response.body.source().use { source ->
                FileSystem.SYSTEM.sink(targetPath).buffer().use { sink ->
                    sink.writeAll(source)
                }
            }

            targetPath.toString()
        }
    }

    /**
     * 从 Content-Disposition 中提取文件名，并进行 URL/UTF-8 解码。
     * 优先解析 filename* (RFC 6266 推荐的 UTF-8 编码)。
     * 返回完全解码后的文件名，可以直接使用。
     */
    private fun parseFileNameFromDisposition(header: String): String? {
        // 1. 尝试匹配 filename*=UTF-8''encodedname 或 filename*=encodedname (如果缺少 UTF-8'')
        // 匹配 filename*=charset''value 或 filename*=value
        val utf8Match = Regex("filename\\*=(?:[^']++'')?([^;]+)").find(header)
        if (utf8Match != null) {
            val encodedName = utf8Match.groupValues[1]
            // filename* 的值是 URL 编码的，需要解码
            return try {
                URLDecoder.decode(encodedName, "UTF-8")
            } catch (_: Exception) {
                encodedName // 解码失败用原值
            }
        }

        // 2. 回退到简单的 filename="name" 或 filename=name (通常是 ASCII/ISO-8859-1)
        val simpleMatch = Regex("filename=\"?([^\";]+)\"?").find(header)
        val name = simpleMatch?.groupValues?.getOrNull(1)

        // 对于 filename 参数，通常不应进行 URL 解码，因为 RFC 并没有保证它是 URL 编码的。
        // 它应该只是一个普通的文件名字符串（可能带引号），我们只返回它，不尝试解码，以避免双重解码错误。
        return name?.trim('"') // 确保移除两侧的引号
    }
}