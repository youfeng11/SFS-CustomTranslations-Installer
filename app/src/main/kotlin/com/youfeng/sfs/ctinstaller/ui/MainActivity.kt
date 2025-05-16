package com.youfeng.sfs.ctinstaller.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionResult
import com.youfeng.sfs.ctinstaller.ui.theme.MainTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

            }
        })
        .build()
    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainTheme {
                MainNavigation(storageHelper, permissionRequest)
            }
        }

    }

}