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
import com.topjohnwu.superuser.Shell
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.core.TAG
import com.youfeng.sfs.ctinstaller.data.model.CTRadioOption
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.model.LatestReleaseApi
import com.youfeng.sfs.ctinstaller.data.model.RadioOption
import com.youfeng.sfs.ctinstaller.data.model.TranslationsApi
import com.youfeng.sfs.ctinstaller.data.repository.FolderRepository
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import com.youfeng.sfs.ctinstaller.data.repository.ShizukuRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map // (关键变更) 只需要 map
import kotlinx.coroutines.flow.stateIn
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
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkRepository: NetworkRepository,
    private val folderRepository: FolderRepository,
    private val shizukuRepository: ShizukuRepository,
    private val settingsRepository: SettingsRepository
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

    private var tempSaveContent: String? = null

    private var customTranslationsUri: Uri? = null

    private val optionList = mutableListOf<CTRadioOption>()

    private val json = Json { ignoreUnknownKeys = true }

    private val customSuCommand: StateFlow<String> = settingsRepository.userSettings
        .map { it.customSuCommand } // 只映射你关心的那一个值
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        // 确保 Shell.setDefaultBuilder 只在 init 时设置一次，使用 customSuCommand 的初始值
        // 移除了对 customSuCommand.collect 的观察
        viewModelScope.launch {
            val command = customSuCommand.first() // 使用 first() 获取初始值并完成
            val builder = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)

            // 使用 .isNotEmpty() 更符合 Kotlin 习惯
            if (command.isNotEmpty()) {
                builder.setCommands(command)
            }

            // 只需要设置一次 libsu 的默认 Builder
            Log.d(TAG, "Shell default builder set once with command: $command")
            Shell.setDefaultBuilder(builder)
        }
    }

    fun onFolderSelected(uri: Uri?) {
        viewModelScope.launch {
            Log.d(TAG, uri.toString())
            Log.d(TAG, sfsDataUri.toString())
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
        if (requestCode == requestCodeInit && grantResult == PackageManager.PERMISSION_GRANTED) {
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
                Log.i(TAG, "检查更新失败", e)
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

    private fun hasSu(): Boolean = try {
        // 检查是否存在 su 二进制文件
        Log.d(TAG, "su二进制检查")
        val suCommand = customSuCommand.value
            .takeIf { it.isNotEmpty() }
            ?: "su"
        val process = Runtime.getRuntime().exec(arrayOf("which", suCommand))
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        process.waitFor()
        Log.d(TAG, "su二进制检查：$suCommand，$output")
        process.waitFor() == 0
    } catch (e: Exception) {
        Log.d(TAG, "su二进制检查出错${e.message}")
        false
    }

    private fun checkShizukuPermission(): Boolean {
        when {
            Shizuku.isPreV11() -> {
                // Pre-v11 is unsupported
                showSnackbar(context.getString(R.string.outdated_shizuku))
                return false
            }

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                // Granted
                shizukuRepository.startUserService()
                return true
            }

            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                showSnackbar(context.getString(R.string.permanently_deny_permissions))
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
            context.getString(R.string.failed_to_retrieve)
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
        setRealOption(-2)
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
        if (_uiState.value.showInstallingDialog) return // 防止重复点击
        _uiState.update {
            it.copy(
                isInstallComplete = false,
                installationProgressText = "",
                showInstallingDialog = true
            )
        }

        val title = optionList.getOrNull(realOption)?.title
        val fs = FileSystem.SYSTEM
        installSaveJob = viewModelScope.launch {
            try {
                val textCachePath = if (realOption == -2) {
                    updateInstallationProgress(context.getString(R.string.installing_process_cached))

                    val cacheFileName =
                        customTranslationsUri?.lastPathSegment?.substringAfterLast('/')
                            ?: (context.getString(R.string.unnamed_translation_file_name) + ".txt")

                    val cacheFile = File(context.externalCacheDir, cacheFileName)

                    context.contentResolver.openInputStream(customTranslationsUri!!)
                        ?.use { inputStream ->
                            FileOutputStream(cacheFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    cacheFile.absolutePath
                } else if (title == null) {
                    val url = Constants.API_URL

                    updateInstallationProgress(context.getString(R.string.installing_process_api_retrieving))
                    val (result, _) = networkRepository.fetchContentFromUrl(url)
                    updateInstallationProgress(context.getString(R.string.installing_process_api_parsing))
                    val customTranslationInfo = if (result.isValidJson()) {
                        json.decodeFromString<CustomTranslationInfo>(result)
                    } else throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))

                    // 检查必要字段
                    if (customTranslationInfo.url.isNullOrBlank() ||
                        customTranslationInfo.compatibleVersion.isNullOrBlank()
                    ) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    }

                    updateInstallationProgress(context.getString(R.string.installing_process_downloading))
                    networkRepository.downloadFileToCache(customTranslationInfo.url)
                } else {
                    updateInstallationProgress(context.getString(R.string.installing_process_api_retrieving))
                    val (result, _) = try {
                        networkRepository.fetchContentFromUrl(Constants.TRANSLATIONS_API_URL)
                    } catch (_: Exception) {
                        networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.TRANSLATIONS_API_URL)
                    }

                    updateInstallationProgress(context.getString(R.string.installing_process_api_parsing))
                    val translationsApi =
                        json.decodeFromString<Map<String, TranslationsApi>>(result)

                    val translationInfo =
                        translationsApi[title]
                            ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    translationInfo.file
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    translationInfo.lang
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    translationInfo.author
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))

                    updateInstallationProgress(context.getString(R.string.installing_process_downloading))
                    try {
                        networkRepository.downloadFileToCache(translationInfo.file)
                    } catch (_: Exception) {
                        networkRepository.downloadFileToCache(Constants.DOWNLOAD_ACCELERATOR_URL + translationInfo.file)
                    }
                }
                updateInstallationProgress(context.getString(R.string.installing_process_installing))

                val target =
                    "${Constants.externalStorage}/${Constants.SFS_CUSTOM_TRANSLATION_DIRECTORY}"
                val fileName = textCachePath.toPath().name

                withContext(Dispatchers.IO) {
                    when (uiState.value.grantedType) {
                        GrantedType.Shizuku -> {
                            updateInstallationProgress(context.getString(R.string.installing_process_verification))
                            if (shizukuRepository.connectionStatus.value
                                        is ShizukuRepository.ConnectionStatus.Connecting
                            ) {
                                updateInstallationProgress(context.getString(R.string.installing_process_shizuku_waiting))
                            }
                            updateInstallationProgress(context.getString(R.string.installing_process_preparing))
                            shizukuRepository.mkdirs(target)
                            updateInstallationProgress(context.getString(R.string.installing_process_copying))
                            shizukuRepository.copyFile(textCachePath, "${target}/$fileName")
                            updateInstallationProgress(context.getString(R.string.installing_copy_successful))
                        }

                        GrantedType.Su -> {
                            updateInstallationProgress(context.getString(R.string.installing_process_preparing))
                            Shell.cmd("mkdir -p \"$target\"").exec()
                            updateInstallationProgress(context.getString(R.string.installing_process_copying))
                            val shellResult =
                                Shell.cmd("cp -f \"$textCachePath\" \"$target/$fileName\"").exec()
                            if (!shellResult.isSuccess) throw IllegalArgumentException(
                                context.getString(
                                    R.string.copy_failed, shellResult.code.toString()
                                )
                            )
                            updateInstallationProgress(context.getString(R.string.installing_copy_successful))
                        }

                        GrantedType.Bug -> {
                            updateInstallationProgress(context.getString(R.string.installing_process_preparing))
                            fs.createDirectories(target.toPathWithZwsp())
                            updateInstallationProgress(context.getString(R.string.installing_process_copying))
                            fs.copy(textCachePath.toPath(), "$target/$fileName".toPathWithZwsp())
                            updateInstallationProgress(context.getString(R.string.installing_copy_successful))
                        }

                        GrantedType.Old -> {
                            updateInstallationProgress(context.getString(R.string.installing_process_preparing))
                            fs.createDirectories(target.toPath())
                            updateInstallationProgress(context.getString(R.string.installing_process_copying))
                            fs.copy(textCachePath.toPath(), "$target/$fileName".toPath())
                            updateInstallationProgress(context.getString(R.string.installing_copy_successful))
                        }

                        GrantedType.Saf -> {
                            updateInstallationProgress(context.getString(R.string.installing_process_verification))
                            val sfsDataDirUri = folderRepository.getPersistedFolderUri()
                                ?: throw IllegalArgumentException(context.getString(R.string.installing_get_persistent_uri_failed))

                            val sourceFile = DocumentFile.fromFile(File(textCachePath))
                            val rootDir = DocumentFile.fromTreeUri(context, sfsDataDirUri)
                                ?: throw IllegalArgumentException(context.getString(R.string.installing_get_persistent_documentfile_failed))

                            updateInstallationProgress(context.getString(R.string.installing_process_preparing))

                            val filesDir = rootDir.findFile("files")
                                ?.takeIf { it.isDirectory }
                                ?: run {
                                    rootDir.findFile("files")?.delete()
                                    rootDir.createDirectory("files")
                                        ?: throw IllegalArgumentException(
                                            context.getString(
                                                R.string.installing_create_directory_unable,
                                                "files"
                                            )
                                        )
                                }

                            val customTranslationsDir = filesDir.findFile("Custom Translations")
                                ?.takeIf { it.isDirectory }
                                ?: run {
                                    filesDir.findFile("Custom Translations")?.delete()
                                    filesDir.createDirectory("Custom Translations")
                                        ?: throw IllegalArgumentException(
                                            context.getString(
                                                R.string.installing_create_directory_unable,
                                                "Custom Translations"
                                            )
                                        )
                                }

                            // 检查目标文件是否存在
                            customTranslationsDir.findFile(fileName)?.let {
                                updateInstallationProgress(context.getString(R.string.installing_process_deleting_conflicting_files))
                                it.delete()
                            }

                            updateInstallationProgress(context.getString(R.string.installing_process_starting))
                            val newFile = customTranslationsDir.createFile("text/plain", fileName)
                                ?: throw IllegalArgumentException(context.getString(R.string.installing_create_file_failed))

                            context.contentResolver.openInputStream(sourceFile.uri)
                                ?.use { inputStream ->
                                    context.contentResolver.openOutputStream(newFile.uri)
                                        ?.use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                }

                            updateInstallationProgress(context.getString(R.string.installing_copy_successful))
                        }
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                val err = e.message ?: context.getString(R.string.unknown_error)
                e.printStackTrace()
                updateInstallationProgress(context.getString(R.string.installing_error, err))
            }
            _uiState.update { it.copy(isInstallComplete = true) }
            updateInstallationProgress(context.getString(R.string.installing_installation_complete))
        }
    }

    /**
     * 点击保存到按钮时的处理逻辑。
     */
    fun onSaveToButtonClick(realOption: Int) {
        if (!_uiState.value.isSavingComplete) {
            showSnackbar(context.getString(R.string.saving_in_progress))
            return
        }
        _uiState.update { it.copy(isSavingComplete = false) }
        showSnackbar(context.getString(R.string.installing_process_downloading))

        val title = optionList.getOrNull(realOption)?.title
        installSaveJob = viewModelScope.launch {
            try {
                val url = if (title == null) {
                    val (result, _) = networkRepository.fetchContentFromUrl(Constants.API_URL)
                    val customTranslationInfo = if (result.isValidJson()) {
                        json.decodeFromString<CustomTranslationInfo>(result)
                    } else throw IllegalArgumentException(context.getString(R.string.installing_api_parsing_failed))

                    // 检查必要字段
                    if (customTranslationInfo.url.isNullOrBlank()) {
                        throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    }
                    customTranslationInfo.url
                } else {
                    val (result, _) = try {
                        networkRepository.fetchContentFromUrl(Constants.TRANSLATIONS_API_URL)
                    } catch (_: Exception) {
                        networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + Constants.TRANSLATIONS_API_URL)
                    }
                    val translationsApi =
                        json.decodeFromString<Map<String, TranslationsApi>>(result)

                    val translationInfo =
                        translationsApi[title]
                            ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                    translationInfo.file
                        ?: throw IllegalArgumentException(context.getString(R.string.installing_api_illegal))
                }

                val (textContent, fileName) = try {
                    networkRepository.fetchContentFromUrl(url)
                } catch (_: Exception) {
                    networkRepository.fetchContentFromUrl(Constants.DOWNLOAD_ACCELERATOR_URL + url)
                }
                tempSaveContent = textContent
                _uiEvent.send(UiEvent.SaveTo(fileName))
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                val err = e.message ?: context.getString(R.string.unknown_error)
                showSnackbar(context.getString(R.string.saving_failed, err))
            } finally {
                _uiState.update { it.copy(isSavingComplete = true) }
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

    fun saveToUri(uri: Uri?) {
        viewModelScope.launch {
            try {
                tempSaveContent?.let { content ->
                    uri ?: run {
                        showSnackbar(context.getString(R.string.save_cancel))
                        return@launch
                    }
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(content)
                    }
                    showSnackbar(context.getString(R.string.save_successful))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val err = e.message ?: context.getString(R.string.unknown_error)
                showSnackbar(context.getString(R.string.saving_failed, err))
            } finally {
                tempSaveContent = null
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
                    if (!hasSu()) context.getString(R.string.permissions_su_not_available) else null
                )
            )
            val options = optionList.sortedByDescending { it.disableInfo.isNullOrEmpty() }
            val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)

            val grantedType = hasStorageAccess()
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
                currentState.copy(appState = newAppState, options = options)
            }
        }
    }

    fun updateStateFromRemote() {
        viewModelScope.launch {
            try {
                val (result, _) = networkRepository.fetchContentFromUrl(Constants.API_URL)
                val customTranslationInfo = json.decodeFromString<CustomTranslationInfo>(result)

                _uiState.update { it.copy(forGameVersion = customTranslationInfo.compatibleVersion!!) }
            } catch (_: Exception) {
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
            } catch (_: Exception) {
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
    private suspend fun hasStorageAccess(): GrantedType? =
        when {
            !isSfsDataDirectoryExists -> null

            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> if (checkStoragePermission(context)) GrantedType.Old else null

            shizukuBinder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                shizukuRepository.startUserService()
                GrantedType.Shizuku
            }

            folderRepository.getPersistedFolderUri() != null -> GrantedType.Saf

            Environment.isExternalStorageManager() && ExploitFileUtil.isExploitable -> GrantedType.Bug

            hasSu() && (Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot) -> GrantedType.Su

            else -> null
        }
}

/**
 * 定义主屏幕的 UI 状态。
 */
data class MainUiState(
    val appState: AppState = AppState.Loading,
    val realOption: Int = -1,
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

    data class SaveTo(val fileName: String) : UiEvent()

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
