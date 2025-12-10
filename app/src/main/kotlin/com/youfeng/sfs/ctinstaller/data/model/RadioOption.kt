package com.youfeng.sfs.ctinstaller.data.model

import com.youfeng.sfs.ctinstaller.ui.viewmodel.GrantedType
import com.youfeng.sfs.ctinstaller.utils.UiText

data class RadioOption(val id: GrantedType, val text: UiText, val disableInfo: UiText? = null)