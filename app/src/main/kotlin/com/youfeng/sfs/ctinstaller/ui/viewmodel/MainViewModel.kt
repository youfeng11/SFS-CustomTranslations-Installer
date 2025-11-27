package com.youfeng.sfs.ctinstaller.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import timber.log.Timber
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CTRadioOption
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.model.LatestReleaseApi
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.data.model.TranslationsApi
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepository
import com.youfeng.sfs.ctinstaller.data.repository.InstallationRepository
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepository
import com.youfeng.sfs.ctinstaller.timber.FileLoggingTree
import com.youfeng.sfs.ctinstaller.utils.DocumentUriUtil
import com.youfeng.sfs.ctinstaller.utils.ExploitFileUtil
import com.youfeng.sfs.ctinstaller.utils.checkStoragePermission
import com.youfeng.sfs.ctinstaller.utils.isAppInstalled
import com.youfeng.sfs.ctinstaller.utils.isDirectoryExists
import com.youfeng.sfs.ctinstaller.utils.isValidJson
import com.youfeng.sfs.ctinstaller.utils.openApp
import com.youfeng.sfs.ctinstaller.utils.toPathWithZwsp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkRepository: NetworkRepository,
    private val folderRepository: FolderRepository,
    private val shizukuRepository: ShizukuRepository,
    private val settingsRepository: SettingsRepository,
    fileLoggingTree: FileLoggingTree,
    private val installationRepository: InstallationRepository
) : ViewModel() {

    private val requestCodeInit = (1..0xFFFF).random()

    // UI 事件，用于触发一次性操作，如显示 Snackbar、启动 Activity 等
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // 主屏幕 UI 状态，包含所有需要展示给用户的数据
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    // 存储安装或保存任务的 Job，用于取消操作
    private var installSaveJob: Job? = null

    // 用于暂存等待下载的 URL
    private var pendingDownloadUrl: String? = null

    private var customTranslationsUri: Uri? = null

    private val optionList = mutableListOf<CTRadioOption>()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        Timber.i(fileLoggingTree.getLatestLogFile().absolutePath)
        Timber.i("MainViewModel 初始化")
        Timber.i("应用版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        Timber.i("设备信息：${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL} ${Build.VERSION.SDK_INT}")
        Timber.i(
            "时区：${
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    "${ZonedDateTime.now().zone.id} UTC${ZonedDateTime.now().offset}"
                } else {
                    TimeZone.getDefault().id
                }
            }"
        )

        Shell.enableVerboseLogging = BuildConfig.DEBUG

        // 确保 Shell.setDefaultBuilder 只在 init 时设置一次，使用 customSuCommand 的初始值
        // 移除了对 customSuCommand.collect 的观察
        viewModelScope.launch {
            val command =
                settingsRepository.userSettings.first().customSuCommand // 使用 first() 获取初始值并完成
            val builder = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)

            // 使用 .isNotEmpty() 更符合 Kotlin 习惯
            if (command.isNotEmpty()) {
                builder.setCommands(command)
            }

            // 只需要设置一次 libsu 的默认 Builder
            Timber.d("Shell default builder set once with command: $command")
            Shell.setDefaultBuilder(builder)
        }
    }

    fun onFolderSelected(uri: Uri?) {
        viewModelScope.launch {
            Timber.v(uri.toString())
            Timber.v(sfsDataUri.toString())
            if (
                uri != null
                && DocumentsContract.isTreeUri(uri)
                && uri == DocumentUriUtil.buildAndroidData(Constants.SFS_PACKAGE_NAME)
            ) {
                showSnackbar(context.getString(R.string.permissions_granted))
                updateMainState()
                folderRepository.persistFolderUri(uri)
            } else {
                showSnackbar(
                    context.getString(R.string.no_permissions_granted),
                    context.getString(R.string.retry)
                ) {
                    onRequestPermissionsClicked(GrantedType.Saf)
                }
            }
        }
    }

    private val sfsDataUri: Uri?
        get() =
            DocumentUriUtil.buildAndroidDataInit(Constants.SFS_PACKAGE_NAME)

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        if (requestCode != requestCodeInit) return
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            updateMainState()
            shizukuRepository.startUserService()
        } else showSnackbar(context.getString(R.string.no_permissions_granted))
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
                    networkRepository.fetchContentFromUrl(Constants.UPDATE_API_URL)
                } catch (_: Exception) {
                    networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.UPDATE_API_URL)
                }
                val latestReleaseInfo = json.decodeFromString<LatestReleaseApi>(result)
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
            Timber.d("Root/su 二进制检查中")

            val suCommand = settingsRepository.userSettings.first().customSuCommand
                .takeIf { it.isNotEmpty() }
                ?: "su"

            val command = arrayOf("which", suCommand)

            process = Runtime.getRuntime().exec(command)

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            val error = process.errorStream.bufferedReader().use(BufferedReader::readText).trim()

            val exitCode = process.waitFor()

            Timber.d("检查命令：$suCommand，退出码：$exitCode，输出：'$output'，错误：'$error'")

            exitCode == 0
        } catch (e: Exception) {
            Timber.e(e, "su 二进制检查出错")
            false
        } finally {
            process?.destroy()
        }
    }

    private fun checkShizukuPermission(): Boolean =
        when {
            Shizuku.isPreV11() -> {
                // Pre-v11 is unsupported
                showSnackbar(context.getString(R.string.outdated_shizuku))
                false
            }

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                shizukuRepository.startUserService()
                true
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                showSnackbar(context.getString(R.string.permanently_deny_permissions))
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
            context.packageManager.getPackageInfo(Constants.SFS_PACKAGE_NAME, 0).versionName!!
        } catch (e: Exception) {
            Timber.w(e, "无法获取SFS版本名")
            null
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
            showSnackbar(context.getString(R.string.permissions_granted))
        } else {
            if (shouldShowRationale) {
                showSnackbar(
                    context.getString(
                        R.string.permission_request_denied,
                        context.getString(R.string.permission_storage)
                    ), context.getString(R.string.retry)
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
     * 取消当前的安装或保存任务。
     */
    fun cancelCurrentTask() {
        installSaveJob?.cancel()
        _uiState.update { it.copy(isInstallComplete = true) }
    }

    /**
     * 点击安装按钮时的处理逻辑。
     */
    fun onInstallButtonClick(realOption: Int) {
        if (_uiState.value.showInstallingDialog) return
        _uiState.update {
            it.copy(
                isInstallComplete = false,
                installationProgressText = "",
                showInstallingDialog = true
            )
        }

        installSaveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 准备源文件 (依然保留在 VM 中，因为它涉及 NetworkRepo 和 UI 选项逻辑)
                // 如果你想更彻底，也可以把这个搬到 Repository，但目前这样已经很好了
                val sourceFileResult = prepareSourceFile(realOption)
                val sourcePath = sourceFileResult.first
                val fileName = sourceFileResult.second

                // 2. 调用 Repository 执行安装
                updateInstallationProgress(context.getString(R.string.installing_process_installing))

                // ✨ 核心变化：一行代码调用 Repository，通过 lambda 更新进度
                installationRepository.installPackage(
                    sourcePath = sourcePath,
                    fileName = fileName,
                    grantedType = uiState.value.grantedType
                ) { progressText ->
                    // Repository 回调这里的代码来更新 UI
                    updateInstallationProgress(progressText)
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                val err = e.message ?: context.getString(R.string.unknown_error)
                Timber.e(e, "安装汉化错误")
                updateInstallationProgress(context.getString(R.string.installing_error, err))
            }
            _uiState.update { it.copy(isInstallComplete = true) }
            updateInstallationProgress(context.getString(R.string.installing_installation_complete))
        }
    }

    /**
     * 准备安装文件。
     * @return Pair<文件绝对路径, 文件名>
     */
    private suspend fun prepareSourceFile(realOption: Int): Pair<String, String> {
        // 情况 1: 本地文件 (Custom Translations Uri)
        if (realOption == TranslationOptionIndices.CUSTOM_FILE) {
            updateInstallationProgress(context.getString(R.string.installing_process_cached))
            val fileName = customTranslationsUri?.lastPathSegment?.substringAfterLast('/')
                ?: (context.getString(R.string.unnamed_translation_file_name) + ".txt")

            val cacheFile = File(context.externalCacheDir, fileName)

            context.contentResolver.openInputStream(customTranslationsUri!!)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            return Pair(cacheFile.absolutePath, fileName)
        }

        val title = optionList.getOrNull(realOption)?.title
        val downloadUrl: String

        // 情况 2: 从 API 获取通用下载链接
        if (title == null) {
            updateInstallationProgress(context.getString(R.string.installing_process_api_retrieving))
            val (result, _) = networkRepository.fetchContentFromUrl(Constants.API_URL)

            updateInstallationProgress(context.getString(R.string.installing_process_api_parsing))
            if (!result.isValidJson()) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))
            }

            val info = json.decodeFromString<CustomTranslationInfo>(result)
            if (info.url.isNullOrBlank() || info.compatibleVersion.isNullOrBlank()) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
            }
            downloadUrl = info.url
        }
        // 情况 3: 从特定翻译列表获取下载链接
        else {
            updateInstallationProgress(context.getString(R.string.installing_process_api_retrieving))
            val (result, _) = try {
                networkRepository.fetchContentFromUrl(Constants.TRANSLATIONS_API_URL)
            } catch (_: Exception) {
                networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.TRANSLATIONS_API_URL)
            }

            updateInstallationProgress(context.getString(R.string.installing_process_api_parsing))
            val apiMap = json.decodeFromString<Map<String, TranslationsApi>>(result)
            val info = apiMap[title]
                ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))

            if (info.file == null || info.lang == null || info.author == null) {
                throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
            }
            downloadUrl = info.file
        }

        // 执行下载
        updateInstallationProgress(context.getString(R.string.installing_process_downloading))
        val downloadedPath = try {
            networkRepository.downloadFileToCache(downloadUrl)
        } catch (_: Exception) {
            // 尝试使用加速器镜像
            networkRepository.downloadFileToCache(Constants.DOWNLOAD_ACCELERATOR_URL + downloadUrl)
        }

        return Pair(downloadedPath, downloadedPath.toPath().name)
    }

    /**
     * 点击保存到按钮时的处理逻辑。
     */
    fun onSaveToButtonClick(realOption: Int) {
        // 如果正在保存中（虽然这里不再预下载，但防止重复点击仍有必要），可以保留状态检查
        if (!_uiState.value.isSavingComplete) {
            showSnackbar(context.getString(R.string.saving_in_progress))
            return
        }

        showSnackbar(context.getString(R.string.installing_process_saving))

        _uiState.update { it.copy(isSavingComplete = false) }

        val title = optionList.getOrNull(realOption)?.title

        // 使用 viewModelScope 仅仅为了解析 API 获取 URL
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // === 保持原有的 URL 解析逻辑 (省略部分代码，保持你原有的 API 解析逻辑不变) ===
                val url = if (title == null) {
                    // ... 解析 API_URL ...
                    val (result, _) = networkRepository.fetchContentFromUrl(Constants.API_URL)
                    if (!result.isValidJson()) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))
                    }
                    val info = json.decodeFromString<CustomTranslationInfo>(result)

                    if (info.url.isNullOrBlank()) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    }
                    info.url
                } else {
                    // ... 解析 TRANSLATIONS_API_URL ...
                    val (result, _) = try {
                        networkRepository.fetchContentFromUrl(Constants.TRANSLATIONS_API_URL)
                    } catch (_: Exception) {
                        networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.TRANSLATIONS_API_URL)
                    }
                    val apiMap = json.decodeFromString<Map<String, TranslationsApi>>(result)
                    apiMap[title]?.file
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                }

                val fileName = try {
                    url.toUri().lastPathSegment ?: "translation.txt"
                } catch (_: Exception) {
                    "translation.txt"
                }

                _uiEvent.send(UiEvent.SaveTo(url, fileName))

            } catch (e: Exception) {
                Timber.e(e, "汉化保存请求错误")
                _uiState.update { it.copy(isSavingComplete = true) }
                val err = e.message ?: context.getString(R.string.unknown_error)
                showSnackbar(context.getString(R.string.saving_failed, err))
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
                        _uiEvent.send(UiEvent.RequestSafPermissions(sfsDataUri))
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
                                    context.getString(
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
            showSnackbar(context.getString(R.string.installing_download_url_lost))
            _uiState.update { it.copy(isSavingComplete = true) }
            return
        }

        // 1. 处理用户取消的情况
        uri ?: run {
            showSnackbar(context.getString(R.string.save_cancel))
            _uiState.update { it.copy(isSavingComplete = true) }
            return
        }

        showSnackbar(context.getString(R.string.installing_process_downloading))

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

                showSnackbar(context.getString(R.string.save_successful))

            } catch (e: Exception) {
                Timber.e(e, "汉化保存失败")
                val err = e.message ?: context.getString(R.string.unknown_error)
                showSnackbar(context.getString(R.string.saving_failed, err))
            } finally {
                _uiState.update { it.copy(isSavingComplete = true) }
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
     */
    fun updateInstallationProgress(text: String) {
        Timber.d("安装进度：$text")
        _uiState.update { currentState ->
            val updatedText = if (currentState.installationProgressText.isNotEmpty()) {
                "${currentState.installationProgressText}\n$text"
            } else {
                text
            }
            currentState.copy(
                installationProgressText = updatedText
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
            val isSu = hasSu()
            val optionList = listOf(
                RadioOption(
                    GrantedType.Shizuku,
                    context.getString(R.string.permissions_shizuku),
                    if (!shizukuBinder)
                        context.getString(R.string.permissions_shizuku_not_available)
                    else null
                ),
                RadioOption(
                    GrantedType.Bug,
                    context.getString(R.string.permissions_exploit),
                    if (!ExploitFileUtil.isExploitable) context.getString(R.string.permissions_unavailable) else null
                ),
                RadioOption(
                    GrantedType.Saf,
                    context.getString(R.string.permissions_saf),
                    null
                ),
                RadioOption(
                    GrantedType.Su,
                    context.getString(R.string.permissions_root),
                    if (!isSu) context.getString(R.string.permissions_su_not_available) else null
                )
            )
            val options = optionList.sortedByDescending { it.disableInfo.isNullOrEmpty() }
            val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)

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
                currentState.copy(appState = newAppState, options = options, sfsVersionName = sfsVersionName)
            }
        }
    }

    fun updateStateFromRemote() {
        viewModelScope.launch {
            try {
                val (result, _) = networkRepository.fetchContentFromUrl(Constants.API_URL)
                val customTranslationInfo = json.decodeFromString<CustomTranslationInfo>(result)

                _uiState.update { it.copy(forGameVersion = customTranslationInfo.compatibleVersion!!) }
            } catch (e: Exception) {
                Timber.e(e, "获取汉化适用版本失败")
                _uiState.update { it.copy(forGameVersion = context.getString(R.string.failed_to_retrieve)) }
            }
            try {
                val (result, _) = try {
                    networkRepository.fetchContentFromUrl(Constants.TRANSLATIONS_API_URL)
                } catch (_: Exception) {
                    networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.TRANSLATIONS_API_URL)
                }
                val translationsApi = json.decodeFromString<Map<String, TranslationsApi>>(result)

                optionList.clear()
                for ((name, translationInfo) in translationsApi) {
                    translationInfo.file ?: throw IllegalArgumentException()
                    translationInfo.lang ?: throw IllegalArgumentException()
                    translationInfo.author ?: throw IllegalArgumentException()
                    val lang = when (translationInfo.lang) {
                        "zh_CN" -> context.getString(R.string.language_simplified_chinese)
                        "zh_TW" -> context.getString(R.string.language_traditional_chinese)
                        else -> translationInfo.lang
                    }
                    optionList.add(
                        CTRadioOption(
                            name,
                            context.getString(
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
    fun showSnackbar(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        Timber.d("Snackbar：$text")
        _uiEvent.trySend(UiEvent.ShowSnackbar(text, actionLabel, action))
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
            _uiEvent.send(UiEvent.RedirectToSystemSettings)
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

/**
 * 定义主屏幕的 UI 状态。
 */
data class MainUiState(
    val appState: AppState = AppState.Loading,
    val realOption: Int = TranslationOptionIndices.DEFAULT_TRANSLATION,
    val showInstallingDialog: Boolean = false,
    val showSettingsRedirectDialog: Boolean = false,
    val installationProgressText: String = "",
    val isInstallComplete: Boolean = false,
    val isSavingComplete: Boolean = true,
    val grantedType: GrantedType = GrantedType.Saf,
    val forGameVersion: String = "Loading...",
    val options: List<RadioOption> = listOf(
        RadioOption(
            id = GrantedType.Old,
            text = "Loading..."
        )
    ),
    val sfsVersionName: String? = null,
    val customTranslationsName: String? = null,
    val ctRadio: List<CTRadioOption>? = listOf(
        CTRadioOption("Loading...")
    ),
    val updateMessage: String? = null
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

    data class SaveTo(val url: String, val fileName: String) : UiEvent()

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

object TranslationOptionIndices {
    // 默认翻译（-1）
    const val DEFAULT_TRANSLATION = -1

    // 本地文件（-2）
    const val CUSTOM_FILE = -2
    // 在线翻译列表中的索引 (>= 0)
}
