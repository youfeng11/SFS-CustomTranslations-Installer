package com.youfeng.sfs.ctinstaller.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorCard(
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
    title: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        // 使用 Material Theme 的错误容器颜色作为背景
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp) // 图标和文本之间的间距
        ) {
            // 错误图标
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Error",
                // 图标颜色会自动继承 contentColor
            )

            // 错误信息内容
            Column {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                    title?.invoke()
                }
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    AnimatedContent(targetState = text) {
                        it.invoke()
                    }
                }
            }
        }
    }
}
