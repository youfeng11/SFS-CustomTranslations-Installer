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
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NetworkRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val client = OkHttpClient()

    suspend fun fetchContentFromUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                response.body.string()
            }
        }
    }

    suspend fun downloadFileToCache(url: String): String {
        // 创建目标文件
        val request = Request.Builder().url(url).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                val source = response.body.source()
                val cacheFile = "${context.externalCacheDir}/${url.md5()}"
                source.use { source ->
                    FileSystem.SYSTEM.sink(cacheFile.toPath()).buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
                cacheFile
            }
        }
    }
}
