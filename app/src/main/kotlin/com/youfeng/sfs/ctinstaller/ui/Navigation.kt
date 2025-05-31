package com.youfeng.sfs.ctinstaller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.ui.screen.MainScreen
import com.youfeng.sfs.ctinstaller.ui.screen.SettingsScreen
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel

@Composable
fun MainNavigation(
    viewModel: MainViewModel,
    storageHelper: SimpleStorageHelper,
    permissionRequest: ActivityPermissionRequest,
    navController: NavHostController = rememberNavController()
) {
    Box {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    viewModel = viewModel,
                    onNavigatorToDetails = { navController.navigate("settings") },
                    storageHelper = storageHelper,
                    permissionRequest = permissionRequest
                )
            }
            composable("settings") { SettingsScreen() }
        }
        Text(
            "内测版本：${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})，仅供演示，不代表最终效果",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}
