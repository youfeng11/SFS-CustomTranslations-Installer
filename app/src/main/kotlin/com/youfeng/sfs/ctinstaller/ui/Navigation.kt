package com.youfeng.sfs.ctinstaller.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.permission.ActivityPermissionRequest
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
}
