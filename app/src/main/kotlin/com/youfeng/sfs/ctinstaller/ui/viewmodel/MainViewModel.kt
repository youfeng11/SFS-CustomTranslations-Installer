package com.youfeng.sfs.ctinstaller.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anggrayudi.storage.SimpleStorage
import com.youfeng.sfs.ctinstaller.core.Constants
import com.youfeng.sfs.ctinstaller.data.model.CustomTranslationInfo
import com.youfeng.sfs.ctinstaller.data.repository.NetworkRepository
import com.youfeng.sfs.ctinstaller.utils.isAppInstalled
import com.youfeng.sfs.ctinstaller.utils.isDirectoryExists
import com.youfeng.sfs.ctinstaller.utils.openApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
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

    var openErrorDialog = mutableStateOf(false)
        private set

    var isEnableInstallButton = mutableStateOf(true)
        private set

    var openInstallingDialog = mutableStateOf(false)
        private set

    var sfsVersionName = mutableStateOf("")
        private set

    private var job: Job? = null

    init {
        // 在 ViewModel 中监听 StateFlow 的变化
        viewModelScope.launch {
            state.collectLatest { value ->
                // 每当 state 的值发生变化，这里就会被调用
                isEnableInstallButton.value = value is MainState.Granted
            }
        }

    }
    /*
        fun checkUpdate() {
            viewModelScope.launch {
                try {
                    val result = networkRepository.fetchContentFromUrl("https://api.github.com/repos/youfeng11/SFS-CustomTranslations-Installer/releases/latest")
                    val customTranslationInfo = if (isValidJson(result)) {
                        Json.decodeFromString(result)
                    } else throw IllegalArgumentException("无法解析API！")
                } catch (e: Exception) {
                }
            }
        }*/

    fun cancelInstalling() {
        job?.cancel()
    }

    private fun isValidJson(jsonStr: String): Boolean = try {
        Json.parseToJsonElement(jsonStr)
        true
    } catch (_: Exception) {
        false
    }

    fun btnInstallOnClick() {
        val url = Constants.API_URL
        job = viewModelScope.launch {
            try {
                _uiEvent.trySend(UiEvent.AddInstallingMessage("正在获取API…"))
                val result = networkRepository.fetchContentFromUrl(url)
                delay(100)
                _uiEvent.trySend(UiEvent.AddInstallingMessage("正在解析API…"))
                delay(100)
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
                _uiEvent.trySend(UiEvent.AddInstallingMessage("正在下载汉化…"))
                val text = networkRepository.downloadFileToCache(customTranslationInfo.url)
                delay(100)
                //val text = "/storage/emulated/0/Android/data/com.youfeng.sfs.ctinstaller/cache/f4e46e0d8b4dfefb2840467717e32dd6"
                _uiEvent.trySend(UiEvent.Install(text))
            } catch (e: Exception) {
                delay(100)
                val err = e.message ?: e
                _uiEvent.trySend(UiEvent.AddInstallingMessage("错误：$err", true))
            }
        }
    }

    fun permissionDialogOnClick() {
        _uiEvent.trySend(UiEvent.RequestPermissions)
    }

    fun openSfs() {
        context.openApp(Constants.SFS_PACKAGE_NAME)
    }

    fun activityOnStart() {
        val isInstalled = context.isAppInstalled(Constants.SFS_PACKAGE_NAME)
        val dataPath = "${SimpleStorage.externalStoragePath}/${Constants.SFS_DATA_DIRECTORY}"

        _state.update {
            when {
                !isInstalled -> MainState.Uninstalled
                !dataPath.toPath().isDirectoryExists() -> MainState.NeverOpened
                SimpleStorage.hasStorageAccess(context, dataPath) -> MainState.Granted
                else -> MainState.Ungranted
            }
        }
        if (isInstalled) {
            sfsVersionName.value =
                context.packageManager.getPackageInfo(Constants.SFS_PACKAGE_NAME, 0).versionName
                    ?: "获取失败"
        }

    }

    fun doOpenErrorDialog() {
        openErrorDialog.value = true
    }

    fun doCloseErrorDialog() {
        openErrorDialog.value = false
    }

    fun doOpenInstallingDialog() {
        openInstallingDialog.value = true
    }

    fun doCloseInstallingDialog() {
        openInstallingDialog.value = false
    }
}

sealed class MainState {
    /** 资源复制中状态 */
    data object Uninstalled : MainState()

    data object NeverOpened : MainState()

    data object Ungranted : MainState()

    data object Granted : MainState()
}

sealed class UiEvent {
    data object RequestPermissions : UiEvent()

    data class AddInstallingMessage(val text: String, val done: Boolean = false) : UiEvent()

    data class Install(val path: String) : UiEvent()
}
