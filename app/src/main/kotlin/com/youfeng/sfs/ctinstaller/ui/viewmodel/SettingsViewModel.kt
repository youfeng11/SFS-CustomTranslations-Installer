package com.youfeng.sfs.ctinstaller.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youfeng.sfs.ctinstaller.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// (更新) Data class 保持不变
data class SettingsUiState(
    val isDarkThemeEnabled: Boolean = false,
    val isFollowingSystem: Boolean = true // 默认值设为 true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository // <-- 2. (新增) 注入仓库
) : ViewModel() {

    // 3. (修改)
    // 将仓库中的 Flow<Boolean> 转换为 UI 使用的 StateFlow<SettingsUiState>
    // 当 DataStore 中的值变化时，uiState 会自动更新
    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.isDarkThemeEnabled,
            settingsRepository.isFollowingSystem
        ) { isDarkTheme, isFollowingSystem ->
            // 当任一 Flow 发出新值时, 创建一个新的 UiState
            SettingsUiState(isDarkTheme, isFollowingSystem)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState() // 使用默认构造函数
            )

    /**
     * 4. (修改) 更新深色主题设置
     */
    fun setDarkTheme(isEnabled: Boolean) {
        // 启动一个协程来调用仓库中的 suspend 函数
        viewModelScope.launch {
            settingsRepository.setDarkTheme(isEnabled)
        }
    }

    fun setFollowingSystem(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFollowingSystem(isEnabled)
        }
    }
}