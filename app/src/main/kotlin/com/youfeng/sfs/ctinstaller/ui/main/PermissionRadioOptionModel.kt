package com.youfeng.sfs.ctinstaller.ui.main

import com.youfeng.sfs.ctinstaller.util.UiText

data class PermissionRadioOptionModel(
    val id: GrantedType,
    val text: UiText,
    val disableInfo: UiText? = null
)