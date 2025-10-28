package com.youfeng.sfs.ctinstaller.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.youfeng.sfs.ctinstaller.ui.theme.MainTheme
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import com.youfeng.sfs.ctinstaller.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var intentUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        handleIntent(intent)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            // 2. 获取 Activity 传递过来的 Uri
            val uriToProcess = intentUri
            // 3. 使用 LaunchedEffect
            // 当 uriToProcess 发生变化时 (且不为null时)，触发 ViewModel 的加载
            LaunchedEffect(uriToProcess) {
                uriToProcess?.let {
                    mainViewModel.handleFileUri(uriToProcess)
                    // 4. 清除 Uri，防止配置更改（如旋转屏幕）时重复加载
                    intentUri = null 
                }
            }
        
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val darkTheme = if (settingsUiState.isFollowingSystem) isSystemInDarkTheme()
            else settingsUiState.isDarkThemeEnabled
            MainTheme(darkTheme) {
                MainNavigation(mainViewModel, settingsViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 5. 当有新 Intent 进来时，更新 State，这将触发 LaunchedEffect
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            // 6. 只更新 State，不直接调用 ViewModel
            intentUri = intent.data
        }
    }
}