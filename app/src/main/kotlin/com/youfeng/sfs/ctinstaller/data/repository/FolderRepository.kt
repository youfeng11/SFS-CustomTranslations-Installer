package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.utils.DocumentUriUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // 使用 SharedPreferences 存储 URI 字符串
    private val prefs: SharedPreferences =
        context.getSharedPreferences("saf_prefs", Context.MODE_PRIVATE)

    /**
     * 获取持久化的文件夹 URI。
     * 检查系统是否仍然授予我们对该 URI 的权限。
     */
    suspend fun getPersistedFolderUri(): Uri? {
        return withContext(Dispatchers.IO) {
            // 关键：检查权限是否仍然有效
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val uri = DocumentUriUtil.buildAndroidData(Constants.SFS_PACKAGE_NAME)
            val hasPermission =
                persistedPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            if (hasPermission) uri else null
        }
    }

    /**
     * 持久化文件夹访问权限。
     * 这是最关键的一步。
     */
    suspend fun persistFolderUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            // 获取持久化权限
            val contentResolver = context.contentResolver
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    /**
     * 清除/撤销权限。
     */
    suspend fun clearPersistedUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            // 释放持久化权限
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        }
    }
}