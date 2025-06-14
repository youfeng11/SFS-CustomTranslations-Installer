package com.youfeng.sfs.ctinstaller.ui

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionReport
import com.anggrayudi.storage.permission.PermissionResult
import com.youfeng.sfs.ctinstaller.ui.theme.MainTheme
import com.youfeng.sfs.ctinstaller.ui.viewmodel.GrantedType
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionRequest = ActivityPermissionRequest.Builder(this)
        .withPermissions(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        .withCallback(object : PermissionCallback {
            override fun onPermissionsChecked(
                result: PermissionResult,
                fromSystemDialog: Boolean
            ) {
                viewModel.onPermissionsChecked(result)
            }

            override fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
                viewModel.setGoToSettingsDialogVisible(true)
            }
        })
        .build()

    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            MainTheme {
                MainNavigation(viewModel, storageHelper, permissionRequest)
            }
        }

        storageHelper.onExpectedStorageNotSelectedEvent = {
            viewModel.showSnackbar("授权失败", "重试") {
                viewModel.onRequestPermissionsClicked(GrantedType.Saf)
            }
        }
        storageHelper.onStorageAccessGranted = { requestCode, root ->
            viewModel.updateMainState()
            viewModel.showSnackbar("授权成功")
        }
    }
}