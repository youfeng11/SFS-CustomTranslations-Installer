package com.youfeng.sfs.ctinstaller.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.data.local.SfsFileConfig
import com.youfeng.sfs.ctinstaller.data.remote.ApiEndpoints
import com.youfeng.sfs.ctinstaller.data.remote.model.CustomTranslationInfoDto
import com.youfeng.sfs.ctinstaller.data.remote.model.LatestReleaseDto
import com.youfeng.sfs.ctinstaller.data.remote.model.TranslationsDto
import com.youfeng.sfs.ctinstaller.data.repository.ContextRepository
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepository
import com.youfeng.sfs.ctinstaller.data.repository.InstallationRepository
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepository
import com.youfeng.sfs.ctinstaller.ui.common.model.RadioOptionModel
import com.youfeng.sfs.ctinstaller.util.DocumentUriUtil
import com.youfeng.sfs.ctinstaller.util.ExploitFileUtil
import com.youfeng.sfs.ctinstaller.util.UiText
import com.youfeng.sfs.ctinstaller.util.checkStoragePermission
import com.youfeng.sfs.ctinstaller.util.isAppInstalled
import com.youfeng.sfs.ctinstaller.util.isDirectoryExists
import com.youfeng.sfs.ctinstaller.util.isValidJson
import com.youfeng.sfs.ctinstaller.util.md5
import com.youfeng.sfs.ctinstaller.util.openApp
import com.youfeng.sfs.ctinstaller.util.toPathWithZwsp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkRepository: NetworkRepository,
    private val folderRepository: FolderRepository,
    private val shizukuRepository: ShizukuRepository,
    private val settingsRepository: SettingsRepository,
    private val installationRepository: InstallationRepository,
    private val contextRepository: ContextRepository
) : ViewModel() {

    private val requestCodeInit = (1..0xFFFF).random()

    // UI 事件，用于触发一次性操作，如显示 Snackbar、启动 Activity 等
    private val _uiEvent = Channel<MainUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // 主屏幕 UI 状态，包含所有需要展示给用户的数据
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // 存储安装任务的 Job，用于取消操作
    private var installJob: Job? = null

    private var customTranslationsUri: Uri? = null

    private val optionList = mutableListOf<RadioOptionModel>()

    private val json = Json { ignoreUnknownKeys = true }

    private val progressLogChannel = Channel<String>(
        capacity = 20, // 设置一个合理的上限，防止 OOM
        onBufferOverflow = BufferOverflow.DROP_OLDEST // 当缓冲区满时，丢弃最旧的消息
    )

    init {
        Timber.i("MainViewModel 初始化")
        setupProgressLogConsumer()
    }

    private fun setupProgressLogConsumer() {
        viewModelScope.launch(Dispatchers.Default) {
            // 使用 while(true) 循环持续等待 Channel 中的新消息
            while (true) {
                // 1. 挂起等待接收第一条消息
                val firstMsg = progressLogChannel.receiveCatching().getOrNull() ?: continue

                val buffer = StringBuilder(firstMsg)

                // 2. 稍作延迟（节流窗口）
                delay(200)

                // 3. 尝试取出 Channel 中积压的所有其他消息
                var nextMsg = progressLogChannel.tryReceive().getOrNull()
                while (nextMsg != null) {
                    buffer.append("\n").append(nextMsg)
                    nextMsg = progressLogChannel.tryReceive().getOrNull()
                }

                // 4. 执行一次 UI 更新
                val textToAppend = buffer.toString()
                _uiState.update { currentState ->
                    val newText = if (currentState.installationProgressText.isNotEmpty()) {
                        "${currentState.installationProgressText}\n$textToAppend"
                    } else {
                        textToAppend
                    }
                    currentState.copy(installationProgressText = newText)
                }
            }
        }
    }

    fun onFolderSelected(uri: Uri?) {
        viewModelScope.launch {
            Timber.v(uri.toString())
            Timber.v(sfsDataUri.toString())
            if (
                uri != null
                && DocumentsContract.isTreeUri(uri)
                && uri == DocumentUriUtil.buildAndroidData(SfsFileConfig.PACKAGE_NAME)
            ) {
                showSnackbar(UiText.StringResource(R.string.permissions_granted))
                updateMainState()
                folderRepository.persistFolderUri(uri)
            } else {
                showSnackbar(
                    UiText.StringResource(R.string.no_permissions_granted),
                    UiText.StringResource(R.string.retry)
                ) {
                    onRequestPermissionsClicked(GrantedType.Saf)
                }
            }
        }
    }

    private val sfsDataUri: Uri?
        get() =
            DocumentUriUtil.buildAndroidDataInit(SfsFileConfig.PACKAGE_NAME)

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        if (requestCode != requestCodeInit) return
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            updateMainState()
            shizukuRepository.startUserService()
        } else showSnackbar(UiText.StringResource(R.string.no_permissions_granted))
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

    private fun checkUpdate() {
        viewModelScope.launch {
            try {
                if (!settingsRepository.userSettings.first().checkUpdate) return@launch
                val (result, _) = try {
                    networkRepository.fetchContentFromUrl(ApiEndpoints.UPDATE_API_URL)
                } catch (_: Exception) {
                    networkRepository.fetchContentFromUrl(ApiEndpoints.DOWNLOAD_ACCELERATOR_URL + ApiEndpoints.UPDATE_API_URL)
                }
                val latestReleaseInfo = json.decodeFromString<LatestReleaseDto>(result)
                val latestVersionCode = latestReleaseInfo.tagName.toInt()
                if (latestVersionCode > BuildConfig.VERSION_CODE) {
                    _uiState.update { it.copy(updateMessage = "${latestReleaseInfo.name} ($latestVersionCode)") }
                }
            } catch (e: Exception) {
                Timber.w(e, "检查更新失败")
            }
        }
    }

    fun activityOnCreate() {
        addShizukuListener()
        checkUpdate()
    }

    private fun addShizukuListener() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    fun removeShizukuListener() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private suspend fun hasSu(): Boolean {
        var process: Process? = null
        return try {
            Timber.i("Root/SU 二进制检查开始")

            val suCommand = settingsRepository.userSettings.first().customSuCommand
                .takeIf { it.isNotEmpty() }
                ?: "su"

            // 使用 ProcessBuilder
            val command = listOf("which", suCommand)

            process = ProcessBuilder(command)
                .redirectErrorStream(true) // 将错误流合并到标准输出流，简化读取
                .start() // 启动进程

            // 读取合并后的输出（包含标准输出和错误输出）
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()

            val exitCode = process.waitFor()

            Timber.i("检查命令：$suCommand，退出码：$exitCode，输出：'$output'")

            // 检查退出码。成功找到可执行文件时，退出码应为 0。
            exitCode == 0
        } catch (e: Exception) {
            Timber.e(e, "Root/SU 二进制检查错误")
            false
        } finally {
            Timber.i("Root/SU 二进制检查结束")
            process?.destroy()
        }
    }

    private fun checkShizukuPermission(): Boolean =
        when {
            Shizuku.isPreV11() -> {
                // Pre-v11 is unsupported
                showSnackbar(UiText.StringResource(R.string.outdated_shizuku))
                false
            }

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                shizukuRepository.startUserService()
                true
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                showSnackbar(UiText.StringResource(R.string.permanently_deny_permissions))
                false
            }

            else -> {
                // Request the permission
                Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
                Shizuku.requestPermission(requestCodeInit)
                false
            }
        }

    private var shizukuBinder: Boolean = false

    // SFS 版本名称的计算属性
    private val sfsVersionName: String?
        get() = try {
            context.packageManager.getPackageInfo(SfsFileConfig.PACKAGE_NAME, 0).versionName!!
        } catch (e: Exception) {
            Timber.w(e, "无法获取SFS版本名")
            null
        }

    fun permissionRequestCheck() {
        viewModelScope.launch {
            _uiEvent.send(MainUiEvent.PermissionRequestCheck)
        }
    }

    /**
     * 处理权限检查结果。
     */
    fun onPermissionsChecked(isGranted: Boolean, shouldShowRationale: Boolean?) {
        shouldShowRationale ?: return
        if (isGranted) {
            updateMainState()
            showSnackbar(UiText.StringResource(R.string.permissions_granted))
        } else {
            if (shouldShowRationale) {
                showSnackbar(
                    UiText.StringResource(
                        R.string.permission_request_denied,
                        UiText.StringResource(R.string.permission_storage)
                    ), UiText.StringResource(R.string.retry)
                ) {
                    permissionRequestCheck()
                }
            } else {
                setGoToSettingsDialogVisible(true)
            }
        }
    }

    fun handleFileUri(uri: Uri) {
        customTranslationsUri = uri
        _uiState.update {
            it.copy(
                customTranslationsName = customTranslationsUri?.lastPathSegment?.substringAfterLast(
                    '/'
                )
            )
        }
        setRealOption(TranslationOptionIndices.CUSTOM_FILE)
    }

    /**
     * 取消当前的安装任务。
     */
    fun cancelinstallationTask() {
        setInstallingDialogVisible(false)
        installJob?.cancel()
        drainProgressChannel()
    }

    private fun drainProgressChannel() {
        // 循环尝试接收，直到通道为空 (返回 null)
        while (progressLogChannel.tryReceive().getOrNull() != null) {
            // 仅仅是为了取出并丢弃，不做任何处理
        }
    }

    /**
     * 点击安装按钮时的处理逻辑。
     */
    fun onInstallButtonClick(realOption: Int) {
        if (_uiState.value.showInstallingDialog) return

        drainProgressChannel()
        _uiState.update {
            it.copy(
                installState = InstallState.Installing,
                installationProgressText = "",
                showInstallingDialog = true
            )
        }

        installJob = viewModelScope.launch(Dispatchers.IO) {
            var caught = false
            try {
                // 1. 准备源文件 (依然保留在 VM 中，因为它涉及 NetworkRepo 和 UI 选项逻辑)
                // 如果你想更彻底，也可以把这个搬到 Repository，但目前这样已经很好了
                val (sourcePath, fileName) = prepareSourceFile(realOption)

                // 2. 调用 Repository 执行安装
                updateInstallationProgress(UiText.StringResource(R.string.installing_process_installing))

                // ✨ 核心变化：一行代码调用 Repository，通过 lambda 更新进度
                installationRepository.installPackage(
                    sourcePath = sourcePath,
                    fileName = fileName,
                    grantedType = uiState.value.grantedType
                ) { progressText ->
                    // Repository 回调这里的代码来更新 UI
                    updateInstallationProgress(progressText)
                }
            } catch (e: CancellationException) {
                Timber.d("安装任务已取消")
                throw e
            } catch (e: Exception) {
                caught = true
                val err =
                    e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(
                        R.string.unknown_error
                    )
                Timber.e(e, "安装汉化错误")
                updateInstallationProgress(UiText.StringResource(R.string.installing_error, err))
            } finally {
                updateInstallationProgress(UiText.StringResource(R.string.installing_installation_complete))
                _uiState.update { it.copy(installState = InstallState.Done(!caught)) }
            }
        }
    }

    /**
     * 准备安装文件。
     * @return Pair<文件绝对路径, 文件名>
     */
    private suspend fun prepareSourceFile(realOption: Int): Pair<String, String> {
        // 情况 1: 本地文件 (Custom Translations Uri)
        if (realOption == TranslationOptionIndices.CUSTOM_FILE) {
            updateInstallationProgress(UiText.StringResource(R.string.installing_process_cached))
            val fileName = customTranslationsUri?.lastPathSegment?.substringAfterLast('/')
                ?: (customTranslationsUri.toString().md5() + ".txt")
            Timber.v("安装本地汉化：${customTranslationsUri.toString()}")

            val cacheFile = File(context.externalCacheDir, fileName)

            context.contentResolver.openInputStream(customTranslationsUri!!)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            return Pair(cacheFile.absolutePath, fileName)
        }

        val title = optionList.getOrNull(realOption)?.title?.let {
            contextRepository.getString(it)
        }
        val downloadUrl: String

        // 情况 2: 从 API 获取通用下载链接
        if (title == null) {
            updateInstallationProgress(UiText.StringResource(R.string.installing_process_api_retrieving))
            val (result, _) = networkRepository.fetchContentFromUrl(ApiEndpoints.API_URL)

            updateInstallationProgress(UiText.StringResource(R.string.installing_process_api_parsing))
            if (!result.isValidJson()) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))
            }

            val info = json.decodeFromString<CustomTranslationInfoDto>(result)
            if (info.url.isNullOrBlank() || info.compatibleVersion.isNullOrBlank()) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
            }
            downloadUrl = info.url
        }
        // 情况 3: 从特定翻译列表获取下载链接
        else {
            updateInstallationProgress(UiText.StringResource(R.string.installing_process_api_retrieving))
            val (result, _) = try {
                networkRepository.fetchContentFromUrl(ApiEndpoints.TRANSLATIONS_API_URL)
            } catch (_: Exception) {
                networkRepository.fetchContentFromUrl(ApiEndpoints.DOWNLOAD_ACCELERATOR_URL + ApiEndpoints.TRANSLATIONS_API_URL)
            }

            updateInstallationProgress(UiText.StringResource(R.string.installing_process_api_parsing))
            val apiMap = json.decodeFromString<Map<String, TranslationsDto>>(result)
            val info = apiMap[title]
                ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))

            if (info.file == null || info.lang == null || info.author == null) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
            }
            downloadUrl = info.file
        }

        // 执行下载
        updateInstallationProgress(UiText.StringResource(R.string.installing_process_downloading))
        val downloadedPath = try {
            networkRepository.downloadFileToCache(downloadUrl)
        } catch (_: Exception) {
            // 尝试使用加速器镜像
            networkRepository.downloadFileToCache(ApiEndpoints.DOWNLOAD_ACCELERATOR_URL + downloadUrl)
        }

        return Pair(downloadedPath, downloadedPath.toPath().name)
    }

    /**
     * 点击保存到按钮时的处理逻辑。
     */
    fun onSaveToButtonClick(realOption: Int) {
        // 如果正在保存中（虽然这里不再预下载，但防止重复点击仍有必要），可以保留状态检查
        if (!_uiState.value.isSavingComplete) {
            showSnackbar(UiText.StringResource(R.string.saving_in_progress))
            return
        }

        showSnackbar(UiText.StringResource(R.string.installing_process_saving))

        _uiState.update { it.copy(isSavingComplete = false) }

        val title = optionList.getOrNull(realOption)?.title?.let {
            contextRepository.getString(it)
        }

        // 使用 viewModelScope 仅仅为了解析 API 获取 URL
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // === 保持原有的 URL 解析逻辑 (省略部分代码，保持你原有的 API 解析逻辑不变) ===
                val url = if (title == null) {
                    // ... 解析 API_URL ...
                    val (result, _) = networkRepository.fetchContentFromUrl(ApiEndpoints.API_URL)
                    if (!result.isValidJson()) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))
                    }
                    val info = json.decodeFromString<CustomTranslationInfoDto>(result)

                    if (info.url.isNullOrBlank()) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    }
                    info.url
                } else {
                    // ... 解析 TRANSLATIONS_API_URL ...
                    val (result, _) = try {
                        networkRepository.fetchContentFromUrl(ApiEndpoints.TRANSLATIONS_API_URL)
                    } catch (_: Exception) {
                        networkRepository.fetchContentFromUrl(ApiEndpoints.DOWNLOAD_ACCELERATOR_URL + ApiEndpoints.TRANSLATIONS_API_URL)
                    }
                    val apiMap = json.decodeFromString<Map<String, TranslationsDto>>(result)
                    apiMap[title]?.file
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                }

                val fileName = try {
                    url.toUri().lastPathSegment ?: "translation.txt"
                } catch (_: Exception) {
                    "translation.txt"
                }

                _uiEvent.send(MainUiEvent.SaveTo(url, fileName))

            } catch (e: Exception) {
                Timber.e(e, "汉化保存请求错误")
                _uiState.update { it.copy(isSavingComplete = true) }
                val err =
                    e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(
                        R.string.unknown_error
                    )
                showSnackbar(UiText.StringResource(R.string.saving_failed, err))
            }
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
                        _uiEvent.send(MainUiEvent.RequestSafPermissions(sfsDataUri))
                    }
                }

                is GrantedType.Shizuku -> {
                    checkShizukuPermission()
                }

                is GrantedType.Su -> {
                    if (Shell.isAppGrantedRoot() != true) {
                        Shell.getShell().close()
                        Shell.getShell { shell ->
                            if (!shell.isRoot)
                                showSnackbar(
                                    UiText.StringResource(
                                        R.string.permission_request_denied,
                                        "ROOT"
                                    )
                                )
                        }
                    }
                    updateMainState()
                }

                else -> {}
            }
        }
    }

    fun saveToUri(uri: Uri?, url: String?) {
        val downloadUrl = url ?: run {
            showSnackbar(UiText.StringResource(R.string.installing_download_url_lost))
            _uiState.update { it.copy(isSavingComplete = true) }
            return
        }

        // 1. 处理用户取消的情况
        uri ?: run {
            showSnackbar(UiText.StringResource(R.string.save_cancel))
            _uiState.update { it.copy(isSavingComplete = true) }
            return
        }

        showSnackbar(UiText.StringResource(R.string.installing_process_downloading))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. 打开目标文件的输出流 (SAF)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    networkRepository.openDownloadStream(downloadUrl).use { inputStream ->
                        // 3. 【核心】流对接：从网络直接复制到文件
                        // 这一步是阻塞的，会持续到下载完成
                        inputStream.copyTo(outputStream)
                    }
                }

                showSnackbar(UiText.StringResource(R.string.save_successful))

            } catch (e: Exception) {
                Timber.e(e, "汉化保存失败")
                val err =
                    e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(
                        R.string.unknown_error
                    )
                showSnackbar(UiText.StringResource(R.string.saving_failed, err))
            } finally {
                _uiState.update { it.copy(isSavingComplete = true) }
            }
        }
    }

    /**
     * 打开 SFS 应用。
     */
    fun openSfs() {
        context.openApp(SfsFileConfig.PACKAGE_NAME)
    }

    /**
     * 更新安装进度文本和安装完成状态。
     * @param text 要追加的进度文本。
     */
    private suspend fun updateInstallationProgress(text: UiText) {
        Timber.d("安装进度：${contextRepository.getString(text)}")
        progressLogChannel.send(contextRepository.getString(text))
        currentCoroutineContext().ensureActive()
    }

    private val isSfsDataDirectoryExists: Boolean
        get() {
            val dataPath = SfsFileConfig.dataDirectoryPath
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
            val isSu = hasSu()
            val optionList = listOf(
                PermissionRadioOptionModel(
                    GrantedType.Shizuku,
                    UiText.StringResource(R.string.permissions_shizuku),
                    if (!shizukuBinder)
                        UiText.StringResource(R.string.permissions_shizuku_not_available)
                    else null
                ),
                PermissionRadioOptionModel(
                    GrantedType.Bug,
                    UiText.StringResource(R.string.permissions_exploit),
                    if (!ExploitFileUtil.isExploitable) UiText.StringResource(R.string.permissions_unavailable) else null
                ),
                PermissionRadioOptionModel(
                    GrantedType.Saf,
                    UiText.StringResource(R.string.permissions_saf),
                    null
                ),
                PermissionRadioOptionModel(
                    GrantedType.Su,
                    UiText.StringResource(R.string.permissions_root),
                    if (!isSu) UiText.StringResource(R.string.permissions_su_not_available) else null
                )
            )
            val options = optionList.sortedByDescending { it.disableInfo == null }
            val isInstalled = context.isAppInstalled(SfsFileConfig.PACKAGE_NAME)

            val grantedType = hasStorageAccess(isSu)
            _uiState.update { currentState ->
                val newAppState = when {
                    !isInstalled -> AppState.Uninstalled

                    !isSfsDataDirectoryExists -> AppState.NeverOpened

                    grantedType != null -> {
                        _uiState.update { it.copy(grantedType = grantedType) }
                        AppState.Granted
                    }

                    else -> AppState.Ungranted
                }
                currentState.copy(
                    appState = newAppState,
                    options = options,
                    sfsVersionName = sfsVersionName
                )
            }
        }
    }

    fun updateStateFromRemote() {
        viewModelScope.launch {
            try {
                val (result, _) = networkRepository.fetchContentFromUrl(ApiEndpoints.API_URL)
                val customTranslationInfo = json.decodeFromString<CustomTranslationInfoDto>(result)

                _uiState.update {
                    it.copy(
                        forGameVersion = UiText.DynamicString(
                            customTranslationInfo.compatibleVersion!!
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "获取汉化适用版本失败")
                _uiState.update { it.copy(forGameVersion = UiText.StringResource(R.string.failed_to_retrieve)) }
            }
            try {
                val (result, _) = try {
                    networkRepository.fetchContentFromUrl(ApiEndpoints.TRANSLATIONS_API_URL)
                } catch (_: Exception) {
                    networkRepository.fetchContentFromUrl(ApiEndpoints.DOWNLOAD_ACCELERATOR_URL + ApiEndpoints.TRANSLATIONS_API_URL)
                }
                val translationsApi = json.decodeFromString<Map<String, TranslationsDto>>(result)

                optionList.clear()
                for ((name, translationInfo) in translationsApi) {
                    translationInfo.file ?: throw IllegalArgumentException()
                    translationInfo.lang ?: throw IllegalArgumentException()
                    translationInfo.author ?: throw IllegalArgumentException()
                    val lang = when (translationInfo.lang) {
                        "zh_CN" -> UiText.StringResource(R.string.language_simplified_chinese)
                        "zh_TW" -> UiText.StringResource(R.string.language_traditional_chinese)
                        else -> UiText.DynamicString(translationInfo.lang)
                    }
                    optionList.add(
                        RadioOptionModel(
                            UiText.DynamicString(name),
                            UiText.StringResource(
                                R.string.language_pack_info,
                                lang,
                                translationInfo.author
                            )
                        )
                    )
                }
                _uiState.update { it.copy(ctRadio = optionList) }
            } catch (e: Exception) {
                Timber.e(e, "获取远程语言包失败")
                _uiState.update { it.copy(ctRadio = null) }
            }
        }
    }

    fun filePicker(uri: Uri?) {
        uri ?: return
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        customTranslationsUri = uri
        _uiState.update {
            it.copy(
                customTranslationsName = customTranslationsUri?.lastPathSegment?.substringAfterLast(
                    '/'
                )
            )
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
    fun showSnackbar(text: UiText, actionLabel: UiText? = null, action: (() -> Unit)? = null) {
        Timber.d("Snackbar：${contextRepository.getString(text)}")
        _uiEvent.trySend(MainUiEvent.ShowSnackbar(text, actionLabel, action))
    }

    /**
     * 设置“安装中”对话框的可见性。
     * @param isVisible 是否可见。
     */
    fun setInstallingDialogVisible(isVisible: Boolean) {
        _uiState.update { it.copy(showInstallingDialog = isVisible) }
    }

    fun setRealOption(realOption: Int) {
        _uiState.update { it.copy(realOption = realOption) }
    }

    /**
     * 设置“前往设置”对话框的可见性。
     * @param isVisible 是否可见。
     */
    fun setGoToSettingsDialogVisible(isVisible: Boolean) {
        _uiState.update { it.copy(showSettingsRedirectDialog = isVisible) }
    }

    /**
     * 重定向到系统设置。
     */
    fun redirectToSystemSettings() {
        viewModelScope.launch {
            _uiEvent.send(MainUiEvent.RedirectToSystemSettings)
        }
    }

    /**
     * 检查是否有存储访问权限。
     * @return 如果有权限，则为返回授权类型；否则为 null。
     */
    private suspend fun hasStorageAccess(isSu: Boolean): GrantedType? =
        when {
            !isSfsDataDirectoryExists -> null

            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> if (checkStoragePermission(context)) GrantedType.Old else null

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                shizukuRepository.startUserService()
                GrantedType.Shizuku
            }

            folderRepository.getPersistedFolderUri() != null -> GrantedType.Saf

            Environment.isExternalStorageManager() && ExploitFileUtil.isExploitable -> GrantedType.Bug

            isSu && (Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot) -> GrantedType.Su

            else -> null
        }
}

object TranslationOptionIndices {
    // 默认翻译（-1）
    const val DEFAULT_TRANSLATION = -1

    // 本地文件（-2）
    const val CUSTOM_FILE = -2
    // 在线翻译列表中的索引 (>= 0)
}
