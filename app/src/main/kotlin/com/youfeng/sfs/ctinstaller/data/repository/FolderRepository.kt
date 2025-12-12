package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.youfeng.sfs.ctinstaller.data.local.SfsFileConfig
import com.youfeng.sfs.ctinstaller.util.DocumentUriUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface FolderRepository {
    suspend fun getPersistedFolderUri(): Uri?

    suspend fun persistFolderUri(uri: Uri)
}

@Singleton
class FolderRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FolderRepository {
    /**
     * 获取持久化的文件夹 URI。
     * 检查系统是否仍然授予我们对该 URI 的权限。
     */
    override suspend fun getPersistedFolderUri(): Uri? {
        return withContext(Dispatchers.IO) {
            // 关键：检查权限是否仍然有效
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val uri = DocumentUriUtil.buildAndroidData(SfsFileConfig.PACKAGE_NAME)
            val hasPermission =
                persistedPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            if (hasPermission) uri else null
        }
    }

    /**
     * 持久化文件夹访问权限。
     * 这是最关键的一步。
     */
    override suspend fun persistFolderUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            // 获取持久化权限
            val contentResolver = context.contentResolver
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        }
    }
}