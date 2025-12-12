package com.youfeng.sfs.ctinstaller.ui.main

import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.ui.common.model.RadioOptionModel
import com.youfeng.sfs.ctinstaller.util.UiText

/**
 * 定义主屏幕的 UI 状态。
 */
data class MainUiState(
    val appState: AppState = AppState.Loading,
    val realOption: Int = TranslationOptionIndices.DEFAULT_TRANSLATION,
    val showInstallingDialog: Boolean = false,
    val showSettingsRedirectDialog: Boolean = false,
    val installationProgressText: String = "",
    val isInstallComplete: Boolean = false,
    val isSavingComplete: Boolean = true,
    val grantedType: GrantedType = GrantedType.Saf,
    val forGameVersion: UiText = UiText.StringResource(R.string.loading),
    val options: List<PermissionRadioOptionModel> = listOf(
        PermissionRadioOptionModel(
            id = GrantedType.Old,
            text = UiText.StringResource(R.string.loading)
        )
    ),
    val sfsVersionName: String? = null,
    val customTranslationsName: String? = null,
    val ctRadio: List<RadioOptionModel>? = listOf(
        RadioOptionModel(UiText.StringResource(R.string.loading))
    ),
    val updateMessage: String? = null
)

/**
 * 定义 SFS 应用的状态。
 */
sealed class AppState {
    data object Loading : AppState()
    data object Uninstalled : AppState()
    data object NeverOpened : AppState()
    data object Ungranted : AppState()
    data object Granted : AppState()
}
