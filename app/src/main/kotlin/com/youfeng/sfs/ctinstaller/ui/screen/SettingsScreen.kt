package com.youfeng.sfs.ctinstaller.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.ui.component.SwitchItem
import com.youfeng.sfs.ctinstaller.ui.viewmodel.SettingsViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigatorToDetails: () -> Unit
) {

    // 从 ViewModel 收集 UI 状态
    val uiState by viewModel.uiState.collectAsState()

    // 基础布局容器
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            onNavigatorToDetails()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    title = {
                        Text(
                            "设置",
                            fontWeight = FontWeight.Black
                        )
                    },
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        .asPaddingValues()
                        .calculateBottomPadding()
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal
                        )
                    ),
            ) {

                // 添加设置项
                item("AutoDarkTheme") {
                    SwitchItem(
                        title = "深色主题跟随系统",
                        summary = "跟随系统设置深色主题",
                        icon = ImageVector.vectorResource(id = R.drawable.ic_night_sight_auto),
                        checked = uiState.isFollowingSystem,
                        onCheckedChange = { viewModel.setFollowingSystem(it) }
                    )
                }
                val themeName = if (uiState.isDarkThemeEnabled) "深色主题" else "浅色主题"
                if (!uiState.isFollowingSystem)
                    item("DarkTheme") {
                        SwitchItem(
                            title = "深色主题",
                            summary = "当前: $themeName",
                            icon = Icons.Filled.DarkMode,
                            checked = uiState.isDarkThemeEnabled,
                            onCheckedChange = { viewModel.setDarkTheme(it) }
                        )
                    }

                item("CheckUpdate") {
                    SwitchItem(
                        title = "检查更新",
                        summary = "在应用启动后自动检查是否有更新",
                        icon = Icons.Default.Update,
                        checked = uiState.checkUpdate,
                        onCheckedChange = { viewModel.setCheckUpdate(it) }
                    )
                }
            }
        }
    }
}
