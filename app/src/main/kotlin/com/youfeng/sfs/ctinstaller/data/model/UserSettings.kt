package com.youfeng.sfs.ctinstaller.data.model

/**
 * 代表用户设置的领域模型。
 * Repository 将暴露这个模型的 Flow。
 */
data class UserSettings(
    val isDarkThemeEnabled: Boolean,
    val isFollowingSystem: Boolean,
    val checkUpdate: Boolean,
    val customSuCommand: String
)