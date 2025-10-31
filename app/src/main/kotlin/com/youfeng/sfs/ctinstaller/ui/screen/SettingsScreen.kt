package com.youfeng.sfs.ctinstaller.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.youfeng.sfs.ctinstaller.R
import com.youfeng.sfs.ctinstaller.ui.component.Item
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

    // 控制 CustomSuCommand 对话框的显示状态
    var showCustomSuCommandDialog by remember { mutableStateOf(false) } //

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

                item("CustomSuCommand") {
                    Item(
                        title = "自定义 su 命令",
                        icon = Icons.Default.Numbers,
                        summary = "请求ROOT权限的命令，通常无需修改"
                    ) {
                        // 点击时显示对话框
                        showCustomSuCommandDialog = true
                    }
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

    // 显示自定义 su 命令对话框
    if (showCustomSuCommandDialog) {
        CustomSuCommandDialog(
            currentCommand = uiState.customSuCommand, // 假设 ViewModel 中有 customSuCommand 属性
            onDismiss = { showCustomSuCommandDialog = false },
            onConfirm = { newCommand ->
                viewModel.setCustomSuCommand(newCommand)
                showCustomSuCommandDialog = false
            }
        )
    }
}

/**
 * 自定义 su 命令的对话框 Composable。
 */
@Composable
fun CustomSuCommandDialog(
    currentCommand: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentCommand) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "自定义 su 命令")
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("su 命令") },
                    placeholder = { Text("默认") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "留空则为默认。修改后需要重启才能生效",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}