package com.youfeng.sfs.ctinstaller.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// 1. (新增) 定义 UI 状态
data class SettingsUiState(
    val isDarkThemeEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    // 2. (新增) 创建私有的 MutableStateFlow 来管理状态
    private val _uiState = MutableStateFlow(SettingsUiState())

    // 3. (新增) 暴露公共的、只读的 StateFlow 供 UI 观察
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * 4. (新增) 更新深色主题设置的函数
     */
    fun setDarkTheme(isEnabled: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(isDarkThemeEnabled = isEnabled)
        }
        // TODO: 在这里, 你通常还需要将这个设置持久化
        // (例如使用 DataStore 或 SharedPreferences)
    }
}