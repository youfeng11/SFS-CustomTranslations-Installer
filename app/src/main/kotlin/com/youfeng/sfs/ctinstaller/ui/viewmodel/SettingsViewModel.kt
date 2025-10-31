package com.youfeng.sfs.ctinstaller.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository // <-- (关键变更) 导入接口
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map // (关键变更) 只需要 map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// UiState 保持不变
data class SettingsUiState(
    val isDarkThemeEnabled: Boolean = false,
    val isFollowingSystem: Boolean = true,
    val checkUpdate: Boolean = true,
    val customSuCommand: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository // <-- (关键变更) 注入接口
) : ViewModel() {

    // (关键变更) ViewModel 变得非常简洁
    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.userSettings // <-- 直接使用 Repository 提供的组合 Flow
            .map { settings ->
                // 将 UserSettings (领域模型) 映射到 SettingsUiState (UI 模型)
                SettingsUiState(
                    isDarkThemeEnabled = settings.isDarkThemeEnabled,
                    isFollowingSystem = settings.isFollowingSystem,
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
}