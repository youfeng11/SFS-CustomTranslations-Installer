package com.youfeng.sfs.ctinstaller.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.result.SingleFileResult
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepository
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepository
import com.youfeng.sfs.ctinstaller.utils.DocumentUriUtil
import com.youfeng.sfs.ctinstaller.utils.ExploitFileUtil
import com.youfeng.sfs.ctinstaller.utils.checkStoragePermission
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
    private val networkRepository: NetworkRepository,
    private val folderRepository: FolderRepository,
    private val shizukuRepository: ShizukuRepository
) : ViewModel() {

    private val requestCodeInit = (Int.MIN_VALUE..Int.MAX_VALUE).random()

    // UI 事件，用于触发一次性操作，如显示 Snackbar、启动 Activity 等
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // 主屏幕 UI 状态，包含所有需要展示给用户的数据
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // 存储安装或保存任务的 Job，用于取消操作
    private var installSaveJob: Job? = null

    private var tempGrantedType: GrantedType? = null

    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    fun onFolderSelected(uri: Uri?) {
        viewModelScope.launch {
            Log.d("SFSCTI", uri.toString())
            Log.d("SFSCTI", sfsDataUri.toString())
            if (
                uri != null
                && DocumentsContract.isTreeUri(uri)
                && uri == DocumentUriUtil.buildAndroidData(Constants.SFS_PACKAGE_NAME)
            ) {
                showSnackbar("授权成功")
                updateMainState()
                folderRepository.persistFolderUri(uri)
            } else {
                showSnackbar("授权失败", "重试") {
                    onRequestPermissionsClicked(GrantedType.Saf)
                }
            }
        }
    }

    private val sfsDataUri: Uri?
        get() =
            DocumentUriUtil.buildAndroidDataInit(Constants.SFS_PACKAGE_NAME)

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        if (requestCode == requestCodeInit && grantResult == PackageManager.PERMISSION_GRANTED) {
            updateMainState()
            shizukuRepository.startUserService()
        } else showSnackbar("授权失败")
        // Do stuff based on the result and the request code
    }

    private val requestPermissionResultListener: OnRequestPermissionResultListener =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            onRequestPermissionsResult(
                requestCode,
                grantResult
            )
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        }

    private val binderReceivedListener = OnBinderReceivedListener {
        shizukuBinder = !Shizuku.isPreV11()
    }
    private val binderDeadListener = OnBinderDeadListener { shizukuBinder = false }

    fun addShizukuListener() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    fun removeShizukuListener() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun hasSu(): Boolean {
        return try {
            // 检查是否存在 su 二进制文件
            Log.d("SFSCTI", "su二进制检查")
            val process = Runtime.getRuntime().exec("which su")
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.d("SFSCTI", "su二进制检查出错${e.message}")
            false
        }
    }

    private fun checkShizukuPermission(): Boolean {
        when {
            Shizuku.isPreV11() -> {
                // Pre-v11 is unsupported
                showSnackbar("请更新Shizuku/Sui至最新版本")
                return false
            }

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                shizukuRepository.startUserService()
                return true
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                showSnackbar("已被永久拒绝")
                return false
            }

            else -> {
                // Request the permission
                Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
                Shizuku.requestPermission(requestCodeInit)
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

    fun permissionRequestCheck() {
        viewModelScope.launch {
            _uiEvent.send(UiEvent.PermissionRequestCheck)
        }
    }

    /**
     * 处理权限检查结果。
     */
    fun onPermissionsChecked(isGranted: Boolean, shouldShowRationale: Boolean?) {
        shouldShowRationale ?: return
        if (isGranted) {
            updateMainState()
            showSnackbar("授权成功")
        } else {
            if (shouldShowRationale) {
                showSnackbar("您拒绝了 存储 权限请求", "重试") {
                    permissionRequestCheck()
                }
            } else {
                setGoToSettingsDialogVisible(true)
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

                val fs = FileSystem.SYSTEM
                var textCachePath = "${context.externalCacheDir}/${customTranslationInfo.url.md5()}"
                updateInstallationProgress("正在获取是否存在缓存…")
                val canUseCache =
                    fs.exists(textCachePath.toPath()) && sha256 == textCachePath.toPath()
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
                    "${Constants.externalStorage}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"

                withContext(Dispatchers.IO) {
                    when (uiState.value.grantedType) {
                        GrantedType.Shizuku -> {
                            if (shizukuRepository.connectionStatus.value
                                        is ShizukuRepository.ConnectionStatus.Connecting
                            ) {
                                updateInstallationProgress("等待FileService连接...")
                            }
                            updateInstallationProgress("准备中...")
                            shizukuRepository.mkdirs(target)
                            updateInstallationProgress("复制中...")
                            shizukuRepository.copyFile(textCachePath, "${target}/简体中文.txt")
                            updateInstallationProgress("复制成功")
                        }

                        GrantedType.Su -> {
                            updateInstallationProgress("准备中...")
                            Shell.cmd("mkdir -p \"$target\"").exec()
                            updateInstallationProgress("复制中...")
                            val shellResult = Shell.cmd("cp -f \"$textCachePath\" \"$target/简体中文.txt\"").exec()
                            if (!shellResult.isSuccess) throw IllegalArgumentException("复制失败：${shellResult.code}")
                            updateInstallationProgress("复制成功")
                        }

                        GrantedType.Bug -> {
                            updateInstallationProgress("准备中...")
                            fs.createDirectories(target.toPathWithZwsp())
                            updateInstallationProgress("复制中...")
                            fs.copy(textCachePath.toPath(), "$target/简体中文.txt".toPathWithZwsp())
                            updateInstallationProgress("复制成功")
                        }

                        GrantedType.Old -> {
                            updateInstallationProgress("准备中...")
                            fs.createDirectories(target.toPath())
                            updateInstallationProgress("复制中...")
                            fs.copy(textCachePath.toPath(), "$target/简体中文.txt".toPath())
                            updateInstallationProgress("复制成功")
                        }

                        GrantedType.Saf -> {
                            val target = if (ExploitFileUtil.isExploitable) target.toPathWithZwsp()
                                .toString() else target
                            file.copyFileTo(
                                context,
                                target,
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
                }
            } catch (e: Exception) {
                val err = e.message ?: "未知错误"
                e.printStackTrace()
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
                    viewModelScope.launch {
                        _uiEvent.send(UiEvent.RequestSafPermissions(sfsDataUri))
                    }
                }

                is GrantedType.Shizuku -> {
                    checkShizukuPermission()
                }

                is GrantedType.Su -> {
                    if (Shell.isAppGrantedRoot() != true) {
                        Shell.getShell().close()
                        Shell.getShell({ shell ->
                            if (!shell.isRoot)
                                showSnackbar("ROOT请求被拒")
                        })
                    }
                    updateMainState()
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
            val dataPath = "${Constants.externalStorage}/${Constants.SFS_DATA_DIRECTORY}"
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
        viewModelScope.launch(Dispatchers.IO) {
            val optionList = listOf(
                RadioOption(
                    GrantedType.Shizuku,
                    "Shizuku/Sui授权",
                    if (!shizukuBinder)
                        "Shizuku/Sui不可用"
                    else null
                ),
                RadioOption(
                    GrantedType.Bug,
                    "漏洞授权",
                    if (!ExploitFileUtil.isExploitable) "您的设备不支持此方式" else null
                ),
                RadioOption(
                    GrantedType.Saf,
                    "SAF授权",
                    null
                ),
                RadioOption(
                    GrantedType.Su,
                    "ROOT授权",
                    if (!hasSu()) "超级用户不可用" else null
                )
            )
            val options = optionList.sortedByDescending { it.disableInfo.isNullOrEmpty() }
            val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)

            _uiState.update { currentState ->
                val newAppState = when {
                    !isInstalled -> AppState.Uninstalled

                    !isSfsDataDirectoryExists -> AppState.NeverOpened

                    hasStorageAccess() != null -> {
                        _uiState.update { it.copy(grantedType = tempGrantedType!!) }
                        AppState.Granted
                    }

                    else -> AppState.Ungranted
                }
                currentState.copy(appState = newAppState, options = options)
            }
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

    override fun onCleared() {
        super.onCleared()
        shizukuRepository.cleanup()
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
        viewModelScope.launch {
            _uiEvent.send(UiEvent.RedirectToSystemSettings)
        }
    }

    /**
     * 检查是否有存储访问权限。
     * @return 如果有权限，则为返回授权类型；否则为 null。
     */
    private suspend fun hasStorageAccess(): GrantedType? {
        tempGrantedType = when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> if (checkStoragePermission(context)) GrantedType.Old else null

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                shizukuRepository.startUserService()
                GrantedType.Shizuku
            }

            folderRepository.getPersistedFolderUri() != null -> GrantedType.Saf

            Environment.isExternalStorageManager() && ExploitFileUtil.isExploitable -> GrantedType.Bug

            Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot -> GrantedType.Su
            
            else -> null
        }
        return tempGrantedType
    }
}

/**
 * 定义主屏幕的 UI 状态。
 */
data class MainUiState(
    val appState: AppState = AppState.Loading,
    val showInstallingDialog: Boolean = false,
    val showGoToSettingsDialog: Boolean = false,
    val installationProgressText: String = "",
    val isInstallComplete: Boolean = false,
    val isSavingComplete: Boolean = true,
    val grantedType: GrantedType = GrantedType.Saf,
    val forGameVersion: String = "加载中...",
    val options: List<RadioOption> = listOf(
        RadioOption(
            id = GrantedType.Old,
            text = "加载中..."
        )
    )
)

/**
 * 定义 SFS 应用的状态。
 */
sealed class AppState {
    data object Loading : AppState()
    data object Uninstalled : AppState()
    data object NeverOpened : AppState()
    data object Ungranted : AppState()
    data object Granted : AppState()
}

/**
 * 定义一次性 UI 事件。
 */
sealed class UiEvent {
    data class RequestSafPermissions(val sfsDataUri: Uri?) : UiEvent()

    data class ShowSnackbar(
        val text: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null
    ) : UiEvent()

    data class SaveTo(val content: String) : UiEvent()

    data object PermissionRequestCheck : UiEvent()

    data object RedirectToSystemSettings : UiEvent()
}

sealed class GrantedType {
    data object Saf : GrantedType()
    data object Old : GrantedType()
    data object Bug : GrantedType()
    data object Shizuku : GrantedType()
    data object Su : GrantedType()
}
