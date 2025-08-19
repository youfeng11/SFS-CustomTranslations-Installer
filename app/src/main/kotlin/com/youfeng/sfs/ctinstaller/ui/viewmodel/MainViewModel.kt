package com.youfeng.sfs.ctinstaller.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.file.StorageType
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.permission.PermissionResult
import com.anggrayudi.storage.result.SingleFileResult
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.utils.ExploitFileUtil
import com.youfeng.sfs.ctinstaller.utils.isAppInstalled
import com.youfeng.sfs.ctinstaller.utils.isDirectoryExists
import com.youfeng.sfs.ctinstaller.utils.isUrl
import com.youfeng.sfs.ctinstaller.utils.isValidJson
import com.youfeng.sfs.ctinstaller.utils.md5
import com.youfeng.sfs.ctinstaller.utils.openApp
import com.youfeng.sfs.ctinstaller.utils.sha256
import com.youfeng.sfs.ctinstaller.utils.toPathWithZwsp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import java.io.File
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkRepository: NetworkRepository
) : ViewModel() {

    init {
        Shizuku.addBinderReceivedListener { shizukuBinder = true }
        Shizuku.addBinderDeadListener { shizukuBinder = false }
    }

    // UI 事件，用于触发一次性操作，如显示 Snackbar、启动 Activity 等
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // 主屏幕 UI 状态，包含所有需要展示给用户的数据
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // 存储安装或保存任务的 Job，用于取消操作
    private var installSaveJob: Job? = null

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) updateMainState() else
            showSnackbar("授权失败")
        // Do stuff based on the result and the request code
    }

    private val requestPermissionResultListener =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                grantResult
            )
        }

    private val binderReceivedListener = OnBinderReceivedListener {
        shizukuBinder = !Shizuku.isPreV11()
    }
    private val binderDeadListener = OnBinderDeadListener { shizukuBinder = false }

    fun addShizukuListener() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun removeShizukuListener() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun checkPermission(): Boolean {
        when {
            Shizuku.isPreV11() -> {
                // Pre-v11 is unsupported
                return false
            }

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                return true
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                return false
            }

            else -> {
                // Request the permission
                Shizuku.requestPermission((Int.MIN_VALUE..Int.MAX_VALUE).random())
                return false
            }
        }
    }

    private var shizukuBinder: Boolean = false

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
        _uiState.update { it.copy(isInstallComplete = true) }
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
                val customTranslationInfo = if (result.isValidJson()) {
                    Json.decodeFromString<CustomTranslationInfo>(result)
                } else throw IllegalArgumentException("无法解析API！")

                // 检查必要字段
                if (customTranslationInfo.url.isNullOrBlank() ||
                    customTranslationInfo.compatibleVersion.isNullOrBlank() ||
                    customTranslationInfo.versionCode == null
                ) {
                    throw IllegalArgumentException("目标API数据不完整或非法！")
                }
                val sha256: String? =
                    customTranslationInfo.sha256?.let {
                        if (it.isUrl()) networkRepository.fetchContentFromUrl(
                            it
                        ) else it
                    }

                val fileSystem = FileSystem.SYSTEM
                var textCachePath = "${context.externalCacheDir}/${customTranslationInfo.url.md5()}"
                updateInstallationProgress("正在获取是否存在缓存…")
                val canUseCache =
                    fileSystem.exists(textCachePath.toPath()) && sha256 == textCachePath.toPath()
                        .sha256()

                if (!canUseCache) {
                    updateInstallationProgress("正在下载汉化…")
                    textCachePath = networkRepository.downloadFileToCache(customTranslationInfo.url)
                    customTranslationInfo.sha256.apply {
                        if (!isNullOrBlank()) {
                            updateInstallationProgress("正在检查完整性…")
                            val sha256 =
                                if (isUrl()) networkRepository.fetchContentFromUrl(this) else this
                            if (sha256 != textCachePath.toPath().sha256())
                                throw IllegalArgumentException("完整性检查未通过，汉化可能被损坏，请尝试重试！")
                        }
                    }
                } else {
                    updateInstallationProgress("存在缓存，跳过下载")
                }
                updateInstallationProgress("正在安装汉化…")

                val file = DocumentFile.fromFile(File(textCachePath))
                val target =
                    "${SimpleStorage.externalStoragePath}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // Android 10 特殊处理
                    fileSystem.createDirectories(target.toPath())
                    fileSystem.copy(textCachePath.toPath(), "$target/简体中文.txt".toPath())
                    updateInstallationProgress("复制成功")
                } else {
                    // Android 11+ 处理
                    val targetFolder = if (uiState.value.grantedType is GrantedType.Bug) {
                        target.toPathWithZwsp().toString()
                    } else {
                        target
                    }
                    withContext(Dispatchers.IO) {
                        file.copyFileTo(
                            context,
                            targetFolder,
                            fileDescription = FileDescription("简体中文.txt"),
                            onConflict = object : SingleFileConflictCallback<DocumentFile>(
                                CoroutineScope(Dispatchers.Main)
                            ) {
                                override fun onFileConflict(
                                    destinationFile: DocumentFile,
                                    action: FileConflictAction
                                ) {
                                    action.confirmResolution(ConflictResolution.REPLACE)
                                }
                            }
                        ).collect {
                            updateInstallationProgress(
                                when (it) {
                                    is SingleFileResult.Validating -> "验证中..."
                                    is SingleFileResult.Preparing -> "准备中..."
                                    is SingleFileResult.CountingFiles -> "正在计算文件..."
                                    is SingleFileResult.DeletingConflictedFile -> "正在删除冲突的文件..."
                                    is SingleFileResult.Starting -> "开始中..."
                                    is SingleFileResult.InProgress -> "进度：${it.progress.toInt()}%"
                                    is SingleFileResult.Completed -> "复制成功"
                                    is SingleFileResult.Error -> "发生错误：${it.errorCode.name}"
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val err = e.message ?: "未知错误"
                updateInstallationProgress("错误：$err")
            }
            updateInstallationProgress("安装结束", true)
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
                val customTranslationInfo = if (result.isValidJson()) {
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
    fun onRequestPermissionsClicked(selectedOption: GrantedType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (selectedOption) {
                is GrantedType.Bug -> {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = ("package:" + context.packageName).toUri()
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    context.startActivity(intent)
                }

                is GrantedType.Saf -> {
                    // 发送事件请求 SAF 权限
                    val fileFullPath =
                        FileFullPath(context, StorageType.EXTERNAL, Constants.SFS_DATA_DIRECTORY)
                    val expectedBasePath = Constants.SFS_DATA_DIRECTORY
                    _uiEvent.trySend(UiEvent.RequestSafPermissions(fileFullPath, expectedBasePath))
                }

                is GrantedType.Shizuku -> {
                    checkPermission()
                }

                else -> {}
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

    private val isSfsDataDirectoryExists: Boolean
        get() {
            val dataPath = "${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}"
            return when {
                dataPath.toPath().isDirectoryExists() -> true

                ExploitFileUtil.isExploitable -> dataPath.toPathWithZwsp()
                    .isDirectoryExists()

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> true

                else -> false
            }
        }

    /**
     * 更新主界面的状态。
     */
    fun updateMainState() {
        val optionList = listOf(
            RadioOption(
                GrantedType.Shizuku,
                "Shizuku授权",
                "待开发的功能"
                /*when {
                    !shizukuBinder -> "Shizuku不可用"
                    else -> null
                }*/
            ),
            RadioOption(
                GrantedType.Su,
                "ROOT授权",
                "待开发的功能"
            ),
            RadioOption(
                GrantedType.Bug,
                "漏洞授权",
                if (!ExploitFileUtil.isExploitable) "您的设备不支持此方式" else null
            ),
            RadioOption(
                GrantedType.Saf,
                "SAF授权",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) "您的设备不支持此方式" else null
            )
        )
        val options = optionList.sortedByDescending { it.disableInfo.isNullOrEmpty() }
        val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)
        val dataPath = "${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}"

        _uiState.update { currentState ->
            val newAppState = when {
                !isInstalled -> AppState.Uninstalled

                !isSfsDataDirectoryExists -> AppState.NeverOpened

                hasStorageAccess(dataPath).first -> {
                    _uiState.update { it.copy(grantedType = hasStorageAccess(dataPath).second) }
                    AppState.Granted
                }

                else -> AppState.Ungranted
            }
            currentState.copy(appState = newAppState, options = options)
        }
    }

    fun updateStateFromRemote() {
        viewModelScope.launch {
            try {
                val result = networkRepository.fetchContentFromUrl(Constants.API_URL)
                val customTranslationInfo = if (result.isValidJson()) {
                    Json.decodeFromString<CustomTranslationInfo>(result)
                } else throw IllegalArgumentException("无法解析API！")

                // 检查必要字段
                if (customTranslationInfo.compatibleVersion.isNullOrBlank())
                    throw IllegalArgumentException("目标API数据不完整或非法！")
                _uiState.update { it.copy(forGameVersion = customTranslationInfo.compatibleVersion) }
            } catch (_: Exception) {
                _uiState.update { it.copy(forGameVersion = "获取失败") }
            }
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
     * 检查是否有存储访问权限。
     * @param dataPath 应用程序数据目录的路径。
     * @return 如果有权限，则为 true；否则为 false。
     */
    private fun hasStorageAccess(dataPath: String): Pair<Boolean, GrantedType> {
        return when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> SimpleStorage.hasStoragePermission(
                context
            ) to GrantedType.Old

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> true to GrantedType.Shizuku

            !Environment.isExternalStorageManager() -> SimpleStorage.hasStorageAccess(
                context,
                dataPath
            ) to GrantedType.Saf

            ExploitFileUtil.isExploitable -> true to GrantedType.Bug

            else -> {
                val accessiblePaths =
                    DocumentFileCompat.getAccessibleAbsolutePaths(context).values.flatten()
                        .toSet()
                accessiblePaths.contains("${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}") to GrantedType.Saf
            }
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
    val isSavingComplete: Boolean = true,
    val grantedType: GrantedType = GrantedType.Saf,
    val forGameVersion: String = "加载中...",
    val options: List<RadioOption> = listOf(
        RadioOption(
            GrantedType.Shizuku,
            "Shizuku授权"
        ),
        RadioOption(
            GrantedType.Su,
            "ROOT授权"
        ),
        RadioOption(
            GrantedType.Bug,
            "漏洞授权"
        ),
        RadioOption(
            GrantedType.Saf,
            "SAF授权"
        )
    )
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
    data object PermissionRequestCheck : UiEvent()
}

sealed class GrantedType {
    data object Saf : GrantedType()
    data object Old : GrantedType()
    data object Bug : GrantedType()
    data object Shizuku : GrantedType()
    data object Su : GrantedType()
}
