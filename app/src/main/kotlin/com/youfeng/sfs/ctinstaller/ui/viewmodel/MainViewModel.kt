package com.youfeng.sfs.ctinstaller.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.permission.PermissionResult
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.utils.ExploitFileUtil
import com.youfeng.sfs.ctinstaller.utils.isAppInstalled
import com.youfeng.sfs.ctinstaller.utils.isDirectoryExists
import com.youfeng.sfs.ctinstaller.utils.openApp
import com.youfeng.sfs.ctinstaller.utils.toPathWithZwsp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkRepository: NetworkRepository
) : ViewModel() {

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _state = MutableStateFlow<MainState>(MainState.Uninstalled)
    val state: StateFlow<MainState> = _state

    private val _openErrorDialog = MutableStateFlow<Boolean>(false)
    val openErrorDialog: StateFlow<Boolean> = _openErrorDialog

    private val _openInstallingDialog = MutableStateFlow<Boolean>(false)
    val openInstallingDialog: StateFlow<Boolean> = _openInstallingDialog

    private val _openPermissionDeniedDialog = MutableStateFlow<Boolean>(false)
    val openPermissionDeniedDialog: StateFlow<Boolean> = _openPermissionDeniedDialog

    private val _openGoToSettingsDialog = MutableStateFlow<Boolean>(false)
    val openGoToSettingsDialog: StateFlow<Boolean> = _openGoToSettingsDialog

    private val _installationProgressText = MutableStateFlow<String>("")
    val installationProgressText: StateFlow<String> = _installationProgressText

    private val _isInstallComplete = MutableStateFlow<Boolean>(false)
    val isInstallComplete: StateFlow<Boolean> = _isInstallComplete

    val sfsVersionName: String
        get() {
            return try {
                context.packageManager.getPackageInfo(Constants.SFS_PACKAGE_NAME, 0).versionName!!
            } catch (_: Exception) {
                "获取失败"
            }
        }

    private var job: Job? = null

    fun onPermissionsChecked(
        result: PermissionResult
    ) {
        if (result.areAllPermissionsGranted) {
            updateMainState()
        } else {
            setPermissionDeniedDialogVisible(true)
        }
    }

    fun cancelInstallation() {
        job?.cancel()
    }

    private fun isValidJson(jsonStr: String): Boolean = try {
        Json.parseToJsonElement(jsonStr)
        true
    } catch (_: Exception) {
        false
    }

    private var lastClickTime = 0L

    fun onInstallButtonClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 500L) {
            lastClickTime = currentTime
            setIsInstallComplete(false)
            clearInstallationProgressText()
            setInstallingDialogVisible(true)
            val url = Constants.API_URL
            job = viewModelScope.launch {
                try {
                    updateInstallationProgressText("正在获取API…")
                    val result = networkRepository.fetchContentFromUrl(url)
                    updateInstallationProgressText("正在解析API…")
                    val customTranslationInfo = if (isValidJson(result)) {
                        Json.decodeFromString<CustomTranslationInfo>(result)
                    } else throw IllegalArgumentException("无法解析API！")
                    if (customTranslationInfo.url.isNullOrBlank()) {
                        throw IllegalArgumentException("目标API没有url值或为非法值！")
                    }
                    if (customTranslationInfo.compatibleVersion.isNullOrBlank()) {
                        throw IllegalArgumentException("目标API没有compatibleVersion值或为非法值！")
                    }
                    customTranslationInfo.versionCode
                        ?: throw IllegalArgumentException("目标API没有versionCode值！")
                    updateInstallationProgressText("正在下载汉化…")
                    val text = networkRepository.downloadFileToCache(customTranslationInfo.url)
                    //val text = "/storage/emulated/0/Android/data/com.youfeng.sfs.ctinstaller/cache/f4e46e0d8b4dfefb2840467717e32dd6"
                    updateInstallationProgressText("正在安装汉化…")
                    val file = DocumentFile.fromFile(File(text))
                    val target = "${SimpleStorage.externalStoragePath}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        val fileSystem = FileSystem.SYSTEM
                        fileSystem.createDirectories(target.toPath())
                        fileSystem.copy(
                            text.toPath(),
                            "$target/简体中文.txt".toPath()
                        )
                        updateInstallationProgressText("复制成功")
                        updateInstallationProgressText("安装结束", true)
                    } else {
                        val targetFolder = if (ExploitFileUtil.isExploitable) {
                            target.toPathWithZwsp()
                                .toString()
                        } else {
                            target
                        }
                        _uiEvent.send(UiEvent.Install(file, targetFolder))
                    }
                } catch (e: Exception) {
                    val err = e.message ?: e
                    updateInstallationProgressText("错误：$err")
                    updateInstallationProgressText("安装结束", true)
                }
            }
        }
    }

    fun onRequestPermissionsClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ExploitFileUtil.isExploitable) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:" + context.packageName).toUri()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                val fileFullPath = FileFullPath(
                    context,
                    StorageType.EXTERNAL,
                    Constants.SFS_DATA_DIRECTORY
                )
                val expectedBasePath = Constants.SFS_DATA_DIRECTORY
                _uiEvent.trySend(UiEvent.RequestSafPermissions(fileFullPath, expectedBasePath))
            }
        }
    }

    fun openSfs() {
        context.openApp(Constants.SFS_PACKAGE_NAME)
    }

    private fun hasStorageAccess(dataPath: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Android 11+ 有权限的
                if (ExploitFileUtil.isExploitable) {
                    true
                } else {
                    val accessiblePaths =
                        DocumentFileCompat.getAccessibleAbsolutePaths(context).values.flatten()
                            .toSet()
                    accessiblePaths.contains("${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}")
                }
            } else {
                // Android 11+ 但没有权限的
                SimpleStorage.hasStorageAccess(
                    context,
                    dataPath
                )
            }
        } else {
            // Android 10-
            SimpleStorage.hasStoragePermission(
                context
            )
        }
    }

    fun updateMainState() {
        val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)
        val dataPath = "${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}"

        _state.update {
            when {
                !isInstalled -> MainState.Uninstalled
                !dataPath.toPath().isDirectoryExists() -> MainState.NeverOpened
                hasStorageAccess(dataPath) -> MainState.Granted
                else -> MainState.Ungranted
            }
        }

    }

    fun setErrorDialogVisibility(isVisible: Boolean) {
        _openErrorDialog.update { isVisible }
    }

    fun setInstallingDialogVisible(isVisible: Boolean) {
        _openInstallingDialog.update { isVisible }
    }

    fun setGoToSettingsDialogVisible(isVisible: Boolean) {
        _openGoToSettingsDialog.update { isVisible }
    }

    fun setPermissionDeniedDialogVisible(isVisible: Boolean) {
        _openPermissionDeniedDialog.update { isVisible }
    }

    fun setIsInstallComplete(isFinished: Boolean) {
        _isInstallComplete.update { isFinished }
    }

    fun updateInstallationProgressText(text: String, isFinished: Boolean? = null) {
        _installationProgressText.update {
            if (!_installationProgressText.value.isEmpty()) {
                "${_installationProgressText.value}\n$text"
            } else {
                text
            }
        }
        isFinished?.let {
            setIsInstallComplete(it)
        }
    }

    fun clearInstallationProgressText() = _installationProgressText.update { "" }

    fun redirectToSystemSettings() {
        SimpleStorageHelper.redirectToSystemSettings(context)
    }
}

sealed class MainState {
    data object Uninstalled : MainState()

    data object NeverOpened : MainState()

    data object Ungranted : MainState()

    data object Granted : MainState()
}

sealed class UiEvent {
    data class RequestSafPermissions(val fileFullPath: FileFullPath, val expectedBasePath: String) :
        UiEvent()

    data class Install(val file: DocumentFile, val targetFolder: String) : UiEvent()
}
