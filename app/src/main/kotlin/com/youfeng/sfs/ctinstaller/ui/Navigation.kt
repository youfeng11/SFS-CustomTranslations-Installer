package com.youfeng.sfs.ctinstaller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.ui.screen.MainScreen
import com.youfeng.sfs.ctinstaller.ui.screen.SettingsScreen
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel
import com.youfeng.sfs.ctinstaller.ui.viewmodel.SettingsViewModel

@Composable
fun MainNavigation(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController()
) {
    Box {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    viewModel = mainViewModel,
                    onNavigatorToDetails = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigatorToDetails = { navController.popBackStack() }
                )
            }
        }
        if (BuildConfig.DEBUG)
            Text(
                stringResource(
                    R.string.debug_warning,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(
                        start = 2.dp,
                        end = 2.dp,
                        bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            .asPaddingValues().calculateBottomPadding()
                    )
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
    }
}
