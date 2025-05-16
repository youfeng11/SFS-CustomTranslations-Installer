package com.youfeng.sfs.ctinstaller.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun OverflowMenu(onNavigatorToDetails: () -> Unit) {
    // 菜单触发按钮
    IconButton(onClick = { onNavigatorToDetails() }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null // 无障碍描述
        )
    }
}
