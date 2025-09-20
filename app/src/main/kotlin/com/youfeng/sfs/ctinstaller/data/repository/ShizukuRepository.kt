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
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var fileService: IFileService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("ShizukuRepository", "Service connected: $service")
            fileService = IFileService.Stub.asInterface(service)
            Log.d("ShizukuRepository", "fileService initialized: $fileService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ShizukuRepository", "Service disconnected")
            fileService = null
        }
    }

    fun startUserService() {
        Log.d("ShizukuRepository", "Starting UserService with args: $args")
        try {
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e("ShizukuRepository", "Failed to bind UserService", e)
        }
    }

    fun cleanup() {
        Log.d("ShizukuRepository", "Cleaning up UserService")
        Shizuku.unbindUserService(args, serviceConnection, true)
        fileService = null
    }

    private val args: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, FileService::class.java.name)
    )
        .processNameSuffix("file_service")
        .daemon(false)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    fun isExists(path: String) = fileService!!.isExists(path)

    fun copyFile(srcPath: String, destPath: String) = fileService!!.copyFile(srcPath, destPath)

    fun mkdirs(path: String) = fileService!!.mkdirs(path)
}