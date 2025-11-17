package com.youfeng.sfs.ctinstaller.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import timber.log.Timber
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.TAG
import com.youfeng.sfs.ctinstaller.service.IShizukuFileService
import com.youfeng.sfs.ctinstaller.service.ShizukuFileService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeoutException

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

    private var fileService: IShizukuFileService? = null

    // 使用 StateFlow 向 ViewModel 暴露连接状态
    private val _connectionStatus =
        MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("Service connected")
            fileService = IShizukuFileService.Stub.asInterface(service)
            _connectionStatus.value = ConnectionStatus.Connected
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Service disconnected")
            fileService = null
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuFileService::class.java.name)
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
            Timber.e(e, "Failed to bind UserService")
            _connectionStatus.value = ConnectionStatus.Error(e)
        }
    }

    // 释放服务连接
    fun cleanup() {
        Timber.d("Cleaning up UserService")
        Shizuku.unbindUserService(args, serviceConnection, true)
        fileService = null
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    /**
     * 等待服务连接成功或失败，带超时机制。
     * 这是一个私有挂起函数，用于处理异步等待逻辑，避免重复代码。
     * @throws IllegalStateException 如果连接失败
     * @throws java.util.concurrent.TimeoutException 如果连接超时
     */
    private suspend fun waitForService(): IShizukuFileService = withContext(Dispatchers.IO) {

        // 使用 withTimeoutOrNull 包装等待逻辑
        val result = withTimeoutOrNull(10000L) {
            // 只有在连接中时才进行等待循环
            while (_connectionStatus.value is ConnectionStatus.Connecting) {
                delay(100) // 等待一小段时间
            }
            // 当状态改变后，返回当前状态
            _connectionStatus.value
        }

        when (result) {
            null -> {
                // 超时退出，result 为 null
                throw TimeoutException(context.getString(R.string.installing_shizuku_timeout))
            }

            is ConnectionStatus.Connected -> fileService
                ?: throw IllegalStateException("Service is connected but fileService is null")

            is ConnectionStatus.Error -> throw IOException(
                "Shizuku service connection failed: ${result.throwable.message}",
                result.throwable
            )

            else -> throw IllegalStateException("Shizuku service is not connecting or connected.")
        }
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