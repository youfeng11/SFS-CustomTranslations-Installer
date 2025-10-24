package com.youfeng.sfs.ctinstaller.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.youfeng.sfs.ctinstaller.R

@Composable
fun OverflowMenu(onNavigatorToDetails: () -> Unit) {
    // 菜单触发按钮
    var openAboutDialog by remember { mutableStateOf(false) }
    IconButton(onClick = {
        openAboutDialog = true
    }) {
        Icon(
            Icons.Default.Info,
            contentDescription = "关于" // 无障碍描述
        )
    }
    if (openAboutDialog)
        AboutDialog(stringResource(R.string.about_source_code)) {
            openAboutDialog = false
        }

    IconButton(onClick = { onNavigatorToDetails() }) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "设置" // 无障碍描述
        )
    }
}
