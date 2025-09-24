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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.youfeng.sfs.ctinstaller.BuildConfig
import com.youfeng.sfs.ctinstaller.ui.screen.MainScreen
import com.youfeng.sfs.ctinstaller.ui.screen.SettingsScreen
import com.youfeng.sfs.ctinstaller.ui.viewmodel.MainViewModel

@Composable
fun MainNavigation(navController: NavHostController = rememberNavController()) {
    Box {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    onNavigatorToDetails = { navController.navigate("settings") }
                )
            }
            composable("settings") { SettingsScreen() }
        }
        Text(
            "内测版本：${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})，仅供演示，不代表最终效果",
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
