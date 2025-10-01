package com.youfeng.sfs.ctinstaller.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.service.FileService
import com.youfeng.sfs.ctinstaller.service.IFileService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // 定义服务连接状态，使用密封类来清晰地表示不同状态
    sealed class ConnectionStatus {
        object Disconnected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        object Connected : ConnectionStatus()
        data class Error(val throwable: Throwable) : ConnectionStatus()
    }

    private var fileService: IFileService? = null

    // 使用 StateFlow 向 ViewModel 暴露连接状态
    private val _connectionStatus =
        MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("ShizukuRepository", "Service connected")
            fileService = IFileService.Stub.asInterface(service)
            _connectionStatus.value = ConnectionStatus.Connected
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ShizukuRepository", "Service disconnected")
            fileService = null
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, FileService::class.java.name)
        )
            .processNameSuffix("file_service")
            .daemon(false)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    // 启动服务，仅在未连接时执行
    fun startUserService() {
        if (_connectionStatus.value is ConnectionStatus.Connected || _connectionStatus.value is ConnectionStatus.Connecting) {
            return
        }

        _connectionStatus.value = ConnectionStatus.Connecting
        try {
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e("ShizukuRepository", "Failed to bind UserService", e)
            _connectionStatus.value = ConnectionStatus.Error(e)
        }
    }

    // 释放服务连接
    fun cleanup() {
        Log.d("ShizukuRepository", "Cleaning up UserService")
        Shizuku.unbindUserService(args, serviceConnection, true)
        fileService = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    /**
     * 等待服务连接成功或失败。
     * 这是一个私有挂起函数，用于处理异步等待逻辑，避免重复代码。
     * @throws IllegalStateException 如果连接失败
     */
    private suspend fun waitForService(): IFileService = withContext(Dispatchers.IO) {
        // 等待服务连接，直到状态变为 Connected 或 Error
        while (_connectionStatus.value is ConnectionStatus.Connecting) {
            delay(100) // 等待一小段时间
        }

        val currentStatus = _connectionStatus.value
        return@withContext when (currentStatus) {
            is ConnectionStatus.Connected -> fileService
                ?: throw IllegalStateException("Service is connected but fileService is null")

            is ConnectionStatus.Error -> throw IOException(
                "Shizuku service connection failed: ${currentStatus.throwable.message}",
                currentStatus.throwable
            )

            else -> throw IllegalStateException("Shizuku service is not connecting or connected.")
        }
    }

    /**
     * 检查文件是否存在。
     * 这是一个挂起函数，调用方（ViewModel）可以在协程中安全地调用。
     * 它会自动等待服务连接成功。
     */
    suspend fun isExists(path: String): Boolean {
        val service = waitForService()
        return service.isExists(path)
    }

    /**
     * 复制文件。
     */
    suspend fun copyFile(srcPath: String, destPath: String) {
        val service = waitForService()
        service.copyFile(srcPath, destPath)
    }

    /**
     * 创建目录。
     */
    suspend fun mkdirs(path: String) {
        val service = waitForService()
        service.mkdirs(path)
    }
}