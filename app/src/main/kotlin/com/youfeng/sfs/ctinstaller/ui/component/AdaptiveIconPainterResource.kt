package com.youfeng.sfs.ctinstaller.ui.component

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.painterResource // 用于兼容旧版本
import androidx.compose.ui.platform.LocalResources

/**
 * 加载自适应图标的 Painter 资源。
 *
 * 如果设备支持 Adaptive Icon (API >= 26) 且资源是 AdaptiveIconDrawable，
 * 则将其转换为 BitmapPainter。否则，回退到标准的 painterResource()。
 */
@Composable
fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val res = LocalResources.current
    val theme = context.theme

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // 尝试加载为 AdaptiveIconDrawable
        val adaptiveIcon = remember(id) {
            ResourcesCompat.getDrawable(res, id, theme) as? AdaptiveIconDrawable
        }

        if (adaptiveIcon != null) {
            // 成功加载，将其转换为 BitmapPainter
            remember(id) {
                // 将 AdaptiveIconDrawable 渲染成 Bitmap
                // 注意：你可能需要调整 toBitmap() 的参数以适应不同的需求，
                // 比如指定宽度、高度或配置，这里使用默认的 intrinstic size。
                BitmapPainter(adaptiveIcon.toBitmap().asImageBitmap())
            }
        } else {
            // 不是 Adaptive Icon，或加载失败，回退到标准的 painterResource
            painterResource(id)
        }
    } else {
        // 不支持 Adaptive Icon 的旧版本，直接使用标准的 painterResource
        painterResource(id)
    }
}