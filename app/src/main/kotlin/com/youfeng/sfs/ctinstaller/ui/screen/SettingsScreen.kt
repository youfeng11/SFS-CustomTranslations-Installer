package com.youfeng.sfs.ctinstaller.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen() {

    // 基础布局容器
    Surface(modifier = Modifier.fillMaxSize()) {
        Text("你好")
    }
}