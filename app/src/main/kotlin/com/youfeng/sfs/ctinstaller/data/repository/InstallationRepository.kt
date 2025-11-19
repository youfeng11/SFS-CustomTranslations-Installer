package com.youfeng.sfs.ctinstaller.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.ui.viewmodel.GrantedType
import com.youfeng.sfs.ctinstaller.utils.toPathWithZwsp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizukuRepository: ShizukuRepository,
    private val folderRepository: FolderRepository
) {

    /**
     * 执行安装的核心方法
     * @param sourcePath 源文件绝对路径
     * @param fileName 文件名
     * @param grantedType 授权类型
     * @param onProgress 进度回调函数
     */
    suspend fun installPackage(
        sourcePath: String,
        fileName: String,
        grantedType: GrantedType,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetDir = "${Constants.externalStorage}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"
        
        Timber.i("Repository: 开始安装，授权方式：$grantedType")

        when (grantedType) {
            GrantedType.Shizuku -> installWithShizuku(sourcePath, targetDir, fileName, onProgress)
            GrantedType.Su -> installWithSu(sourcePath, targetDir, fileName, onProgress)
            GrantedType.Bug -> installWithExploit(sourcePath, targetDir, fileName, onProgress)
            GrantedType.Old -> installWithLegacyStorage(sourcePath, targetDir, fileName, onProgress)
            GrantedType.Saf -> installWithSaf(sourcePath, fileName, onProgress)
        }
    }

    private suspend fun installWithShizuku(source: String, targetDir: String, fileName: String, onProgress: (String) -> Unit) {
        onProgress(context.getString(R.string.installing_process_verification))
        if (shizukuRepository.connectionStatus.value is ShizukuRepository.ConnectionStatus.Connecting) {
            onProgress(context.getString(R.string.installing_process_shizuku_waiting))
        }
        onProgress(context.getString(R.string.installing_process_preparing))
        shizukuRepository.mkdirs(targetDir)

        onProgress(context.getString(R.string.installing_process_copying))
        shizukuRepository.copyFile(source, "$targetDir/$fileName")
        onProgress(context.getString(R.string.installing_copy_successful))
    }

    private fun installWithSu(source: String, targetDir: String, fileName: String, onProgress: (String) -> Unit) {
        onProgress(context.getString(R.string.installing_process_preparing))
        Shell.cmd("mkdir -p \"$targetDir\"").exec()

        onProgress(context.getString(R.string.installing_process_copying))
        val shellResult = Shell.cmd("cp -f \"$source\" \"$targetDir/$fileName\"").exec()

        if (!shellResult.isSuccess) {
            throw IllegalArgumentException(context.getString(R.string.copy_failed, shellResult.code.toString()))
        }
        onProgress(context.getString(R.string.installing_copy_successful))
    }

    private fun installWithExploit(source: String, targetDir: String, fileName: String, onProgress: (String) -> Unit) {
        onProgress(context.getString(R.string.installing_process_preparing))
        val targetDirPath = targetDir.toPathWithZwsp()
        val targetFile = targetDirPath / fileName

        FileSystem.SYSTEM.createDirectories(targetDirPath)

        onProgress(context.getString(R.string.installing_process_copying))
        FileSystem.SYSTEM.delete(targetFile, mustExist = false)
        FileSystem.SYSTEM.copy(source.toPath(), targetFile)
        onProgress(context.getString(R.string.installing_copy_successful))
    }

    private fun installWithLegacyStorage(source: String, targetDir: String, fileName: String, onProgress: (String) -> Unit) {
        onProgress(context.getString(R.string.installing_process_preparing))
        FileSystem.SYSTEM.createDirectories(targetDir.toPath())

        onProgress(context.getString(R.string.installing_process_copying))
        FileSystem.SYSTEM.copy(source.toPath(), "$targetDir/$fileName".toPath())
        onProgress(context.getString(R.string.installing_copy_successful))
    }

    private suspend fun installWithSaf(sourcePath: String, fileName: String, onProgress: (String) -> Unit) {
        onProgress(context.getString(R.string.installing_process_verification))
        val sfsDataDirUri = folderRepository.getPersistedFolderUri()
            ?: throw IllegalArgumentException(context.getString(R.string.installing_get_persistent_uri_failed))

        val rootDir = DocumentFile.fromTreeUri(context, sfsDataDirUri)
            ?: throw IllegalArgumentException(context.getString(R.string.installing_get_persistent_documentfile_failed))

        onProgress(context.getString(R.string.installing_process_preparing))

        val filesDir = rootDir.findFile("files")?.takeIf { it.isDirectory }
            ?: run {
                rootDir.findFile("files")?.delete()
                rootDir.createDirectory("files")
                    ?: throw IllegalArgumentException(context.getString(R.string.installing_create_directory_unable, "files"))
            }

        val customTranslationsDir = filesDir.findFile("Custom Translations")?.takeIf { it.isDirectory }
            ?: run {
                filesDir.findFile("Custom Translations")?.delete()
                filesDir.createDirectory("Custom Translations")
                    ?: throw IllegalArgumentException(context.getString(R.string.installing_create_directory_unable, "Custom Translations"))
            }

        customTranslationsDir.findFile(fileName)?.let {
            onProgress(context.getString(R.string.installing_process_deleting_conflicting_files))
            it.delete()
        }

        onProgress(context.getString(R.string.installing_process_starting))
        val newFile = customTranslationsDir.createFile("text/plain", fileName)
            ?: throw IllegalArgumentException(context.getString(R.string.installing_create_file_failed))

        context.contentResolver.openInputStream(Uri.fromFile(File(sourcePath)))?.use { input ->
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                input.copyTo(output)
            }
        }

        onProgress(context.getString(R.string.installing_copy_successful))
    }
}