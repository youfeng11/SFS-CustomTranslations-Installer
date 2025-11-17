package com.youfeng.sfs.ctinstaller.timber

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Singleton
class FileLoggingTree @Inject constructor(
    private val context: Context
) : Timber.Tree() {

    private val logFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        val logDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        logFile = File(logDir, "app_log_${System.currentTimeMillis()}.txt")
    }

    private fun createTag(): String {
        val stackTrace = Throwable().stackTrace

        // 跳过 Timber 内部类 & 当前 Tree 类
        for (element in stackTrace) {
            val className = element.className

            if (!className.startsWith("timber.log.")
                && className != this::class.java.name
            ) {
                val simpleName = className.substringAfterLast('.')
                return "$simpleName:${element.lineNumber}"
            }
        }
        return "Unknown"
    }


    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val realTag = tag ?: createTag()
            val logTime = dateFormat.format(Date())
            val priorityStr = when (priority) {
                Log.VERBOSE -> "V"; Log.DEBUG -> "D"; Log.INFO -> "I"
                Log.WARN -> "W"; Log.ERROR -> "E"; Log.ASSERT -> "A"
                else -> priority.toString()
            }
            val logMessage = "$logTime $priorityStr/$realTag: $message\n"
            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "写日志失败", e)
        }
    }

    fun getLatestLogFile(): File = logFile
}