package com.youfeng.sfs.ctinstaller.ui.settings

// UiState 保持不变
data class SettingsUiState(
    val isDarkThemeEnabled: Boolean = false,
    val isFollowingSystem: Boolean = true,
    val isDynamicColor: Boolean = false,
    val checkUpdate: Boolean = true,
    val customSuCommand: String = ""
)