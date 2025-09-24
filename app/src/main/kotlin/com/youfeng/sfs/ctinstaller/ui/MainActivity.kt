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
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionReport
import com.anggrayudi.storage.permission.PermissionResult
import com.youfeng.sfs.ctinstaller.ui.theme.MainTheme
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                MainNavigation()
            }
        }
    }
}