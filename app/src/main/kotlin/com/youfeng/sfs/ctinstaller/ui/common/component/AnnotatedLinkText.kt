package com.youfeng.sfs.ctinstaller.ui.common.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * HTML链接文本解析器
 * @param htmlString 包含<a>标签的HTML字符串
 * 特性：
 * - 链接显示为带下划线的主题色
 * - 按压状态有背景高亮
 * - 自动解析HTML实体
 */
@Composable
fun AnnotatedLinkText(htmlString: String) {
    val annotatedString = AnnotatedString.fromHtml(
        htmlString = htmlString,
        linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            ),
            pressedStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        )
    )
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp
    )
}