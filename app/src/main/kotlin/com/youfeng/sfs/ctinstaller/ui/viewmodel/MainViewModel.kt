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
import kotlinx.coroutines.CancellationException
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

    // UI 事件，用于触发一次性操作，如显示 Snackbar、启动 Activity 等
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // 主屏幕 UI 状态，包含所有需要展示给用户的数据
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // 存储安装或保存任务的 Job，用于取消操作
    private var installSaveJob: Job? = null

    // SFS 版本名称的计算属性
    val sfsVersionName: String
        get() = try {
            context.packageManager.getPackageInfo(Constants.SFS_PACKAGE_NAME, 0).versionName!!
        } catch (_: Exception) {
            "获取失败"
        }

    /**
     * 处理权限检查结果。
     * @param result 权限请求的结果。
     */
    fun onPermissionsChecked(result: PermissionResult) {
        if (result.areAllPermissionsGranted) {
            updateMainState()
            showSnackbar("授权成功")
        } else {
            showSnackbar("您拒绝了 存储 权限请求", "重试") {
                _uiEvent.trySend(UiEvent.PermissionRequestCheck)
            }
        }
    }

    /**
     * 取消当前的安装或保存任务。
     */
    fun cancelCurrentTask() {
        installSaveJob?.cancel()
        _uiState.update {
            it.copy(
                installationProgressText = "任务已取消",
                isInstallComplete = true
            )
        }
    }

    /**
     * 点击安装按钮时的处理逻辑。
     */
    fun onInstallButtonClick() {
        if (_uiState.value.showInstallingDialog) return // 防止重复点击
        _uiState.update {
            it.copy(
                isInstallComplete = false,
                installationProgressText = "",
                showInstallingDialog = true
            )
        }

        val url = Constants.API_URL
        installSaveJob = viewModelScope.launch {
            try {
                updateInstallationProgress("正在获取API…")
                val result = networkRepository.fetchContentFromUrl(url)
                updateInstallationProgress("正在解析API…")
                val customTranslationInfo = if (isValidJson(result)) {
                    Json.decodeFromString<CustomTranslationInfo>(result)
                } else throw IllegalArgumentException("无法解析API！")

                // 检查必要字段
                if (customTranslationInfo.url.isNullOrBlank() ||
                    customTranslationInfo.compatibleVersion.isNullOrBlank() ||
                    customTranslationInfo.versionCode == null
                ) {
                    throw IllegalArgumentException("目标API数据不完整或非法！")
                }

                updateInstallationProgress("正在下载汉化…")
                val textCachePath = networkRepository.downloadFileToCache(customTranslationInfo.url)
                updateInstallationProgress("正在安装汉化…")

                val file = DocumentFile.fromFile(File(textCachePath))
                val target =
                    "${SimpleStorage.externalStoragePath}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // Android 10 特殊处理
                    val fileSystem = FileSystem.SYSTEM
                    fileSystem.createDirectories(target.toPath())
                    fileSystem.copy(textCachePath.toPath(), "$target/简体中文.txt".toPath())
                    updateInstallationProgress("复制成功", true)
                } else {
                    // Android 11+ 处理
                    val targetFolder = if (ExploitFileUtil.isExploitable) {
                        target.toPathWithZwsp().toString()
                    } else {
                        target
                    }
                    _uiEvent.send(UiEvent.Install(file, targetFolder))
                }
            } catch (_: CancellationException) {
                // 协程被取消，不进行错误提示
                _uiState.update {
                    it.copy(
                        installationProgressText = "汉化安装中止",
                        isInstallComplete = true
                    )
                }
            } catch (e: Exception) {
                val err = e.message ?: "未知错误"
                updateInstallationProgress("错误：$err", true)
            }
        }
    }

    /**
     * 点击保存到按钮时的处理逻辑。
     */
    fun onSaveToButtonClick() {
        if (!_uiState.value.isSavingComplete) {
            showSnackbar("保存正在进行中，请勿频繁点击")
            return
        }
        _uiState.update { it.copy(isSavingComplete = false) }
        showSnackbar("正在下载汉化…")

        val url = Constants.API_URL
        installSaveJob = viewModelScope.launch {
            try {
                val result = networkRepository.fetchContentFromUrl(url)
                val customTranslationInfo = if (isValidJson(result)) {
                    Json.decodeFromString<CustomTranslationInfo>(result)
                } else throw IllegalArgumentException("无法解析API！")

                // 检查必要字段
                if (customTranslationInfo.url.isNullOrBlank() ||
                    customTranslationInfo.compatibleVersion.isNullOrBlank() ||
                    customTranslationInfo.versionCode == null
                ) {
                    throw IllegalArgumentException("目标API数据不完整或非法！")
                }

                val textContent = networkRepository.fetchContentFromUrl(customTranslationInfo.url)
                _uiEvent.send(UiEvent.SaveTo(textContent))
            } catch (_: CancellationException) {
                // 协程被取消，不进行错误提示
            } catch (e: Exception) {
                val err = e.message ?: "未知错误"
                showSnackbar("无法保存汉化：$err")
            }
            _uiState.update { it.copy(isSavingComplete = true) }
        }
    }

    /**
     * 请求 SAF 权限或跳转到系统设置。
     */
    fun onRequestPermissionsClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ExploitFileUtil.isExploitable) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = ("package:" + context.packageName).toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                // 发送事件请求 SAF 权限
                val fileFullPath =
                    FileFullPath(context, StorageType.EXTERNAL, Constants.SFS_DATA_DIRECTORY)
                val expectedBasePath = Constants.SFS_DATA_DIRECTORY
                _uiEvent.trySend(UiEvent.RequestSafPermissions(fileFullPath, expectedBasePath))
            }
        }
    }

    /**
     * 打开 SFS 应用。
     */
    fun openSfs() {
        context.openApp(Constants.SFS_PACKAGE_NAME)
    }

    /**
     * 更新安装进度文本和安装完成状态。
     * @param text 要追加的进度文本。
     * @param isFinished 安装是否完成。
     */
    fun updateInstallationProgress(text: String, isFinished: Boolean? = null) {
        _uiState.update { currentState ->
            val updatedText = if (currentState.installationProgressText.isNotEmpty()) {
                "${currentState.installationProgressText}\n$text"
            } else {
                text
            }
            currentState.copy(
                installationProgressText = updatedText,
                isInstallComplete = isFinished ?: currentState.isInstallComplete
            )
        }
    }

    /**
     * 更新主界面的状态。
     */
    fun updateMainState() {
        val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)
        val dataPath = "${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}"

        _uiState.update { currentState ->
            val newAppState = when {
                !isInstalled -> AppState.Uninstalled
                !dataPath.toPath().isDirectoryExists() -> AppState.NeverOpened
                hasStorageAccess(dataPath) -> AppState.Granted
                else -> AppState.Ungranted
            }
            currentState.copy(appState = newAppState)
        }
    }

    /**
     * 显示 Snackbar。
     * @param text 要显示的消息。
     * @param actionLabel Snackbar 动作的标签。
     * @param action Snackbar 动作被点击时执行的回调。
     */
    fun showSnackbar(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _uiEvent.trySend(UiEvent.ShowSnackbar(text, actionLabel, action))
    }

    /**
     * 设置“安装中”对话框的可见性。
     * @param isVisible 是否可见。
     */
    fun setInstallingDialogVisible(isVisible: Boolean) {
        _uiState.update { it.copy(showInstallingDialog = isVisible) }
    }

    /**
     * 设置“前往设置”对话框的可见性。
     * @param isVisible 是否可见。
     */
    fun setGoToSettingsDialogVisible(isVisible: Boolean) {
        _uiState.update { it.copy(showGoToSettingsDialog = isVisible) }
    }

    /**
     * 重定向到系统设置。
     */
    fun redirectToSystemSettings() {
        SimpleStorageHelper.redirectToSystemSettings(context)
    }

    /**
     * 检查 JSON 字符串是否有效。
     * @param jsonStr 要检查的 JSON 字符串。
     * @return 如果是有效 JSON，则为 true；否则为 false。
     */
    private fun isValidJson(jsonStr: String): Boolean = try {
        Json.parseToJsonElement(jsonStr)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 检查是否有存储访问权限。
     * @param dataPath 应用程序数据目录的路径。
     * @return 如果有权限，则为 true；否则为 false。
     */
    private fun hasStorageAccess(dataPath: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (ExploitFileUtil.isExploitable) {
                    true
                } else {
                    val accessiblePaths =
                        DocumentFileCompat.getAccessibleAbsolutePaths(context).values.flatten()
                            .toSet()
                    accessiblePaths.contains("${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}")
                }
            } else {
                SimpleStorage.hasStorageAccess(context, dataPath)
            }
        } else {
            SimpleStorage.hasStoragePermission(context)
        }
    }
}

/**
 * 定义主屏幕的 UI 状态。
 */
data class MainUiState(
    val appState: AppState = AppState.Uninstalled,
    val showInstallingDialog: Boolean = false,
    val showGoToSettingsDialog: Boolean = false,
    val installationProgressText: String = "",
    val isInstallComplete: Boolean = false,
    val isSavingComplete: Boolean = true
)

/**
 * 定义 SFS 应用的状态。
 */
sealed class AppState {
    data object Uninstalled : AppState()
    data object NeverOpened : AppState()
    data object Ungranted : AppState()
    data object Granted : AppState()
}

/**
 * 定义一次性 UI 事件。
 */
sealed class UiEvent {
    data class RequestSafPermissions(val fileFullPath: FileFullPath, val expectedBasePath: String) :
        UiEvent()

    data class ShowSnackbar(
        val text: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null
    ) : UiEvent()

    data class SaveTo(val content: String) : UiEvent()
    data class Install(val file: DocumentFile, val targetFolder: String) : UiEvent()
    data object PermissionRequestCheck : UiEvent()
}
