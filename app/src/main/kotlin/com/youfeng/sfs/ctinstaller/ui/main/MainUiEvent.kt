package com.youfeng.sfs.ctinstaller.ui.main

import android.net.Uri
import com.youfeng.sfs.ctinstaller.util.UiText

/**
 * 定义一次性 UI 事件。
 */
sealed class MainUiEvent {
    data class RequestSafPermissions(val sfsDataUri: Uri?) : MainUiEvent()

    data class ShowSnackbar(
        val text: UiText,
        val actionLabel: UiText? = null,
        val action: (() -> Unit)? = null
    ) : MainUiEvent()

    data class SaveTo(val url: String, val fileName: String) : MainUiEvent()

    data object PermissionRequestCheck : MainUiEvent()

    data object RedirectToSystemSettings : MainUiEvent()
}

sealed class GrantedType {
    data object Saf : GrantedType()
    data object Old : GrantedType()
    data object Bug : GrantedType()
    data object Shizuku : GrantedType()
    data object Su : GrantedType()
}