package com.youfeng.sfs.ctinstaller.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youfeng.sfs.ctinstaller.common.logging.FileLoggingTree
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val fileLoggingTree: FileLoggingTree
) : ViewModel() {

    init {
        Timber.i("SettingsViewModel 初始化")
    }

    // (关键变更) ViewModel 变得非常简洁
    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.userSettings // <-- 直接使用 Repository 提供的组合 Flow
            .map { settings ->
                // 将 UserSettings (领域模型) 映射到 SettingsUiState (UI 模型)
                SettingsUiState(
                    isDarkThemeEnabled = settings.isDarkThemeEnabled,
                    isFollowingSystem = settings.isFollowingSystem,
                    isDynamicColor = settings.isDynamicColor,
                    checkUpdate = settings.checkUpdate,
                    customSuCommand = settings.customSuCommand
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState() // 初始值
            )

    // (无变更) 所有 'set' 方法保持不变，它们现在调用的是接口方法
    fun setDarkTheme(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(isEnabled)
        }
    }

    fun setFollowingSystem(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFollowingSystem(isEnabled)
        }
    }

    fun setDynamicColor(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(isEnabled)
        }
    }

    fun setCheckUpdate(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCheckUpdate(isEnabled)
        }
    }

    fun setCustomSuCommand(command: String) {
        viewModelScope.launch {
            settingsRepository.setCustomSuCommand(command)
        }
    }

    fun onShareLog() {
        val file = fileLoggingTree.getLatestLogFile()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "应用日志 - ${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                    ).format(Date())
                }"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "分享日志").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // ⭐Chooser 也要加
            }
        )
    }
}