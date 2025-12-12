package com.youfeng.sfs.ctinstaller.data.remote

object ApiEndpoints {
    // 基础 API 数据
    const val API_URL = "https://gitee.com/YouFeng11/SFS-zh-CN-Translation/raw/master/info.json"

    // 翻译列表 API
    const val TRANSLATIONS_API_URL =
        "https://raw.githubusercontent.com/youfeng11/SFS-CustomTranslations-Installer/refs/heads/main/api/translations.json"

    // 应用更新检查 API
    const val UPDATE_API_URL =
        "https://api.github.com/repos/youfeng11/SFS-CustomTranslations-Installer/releases/latest"

    // 下载加速前缀
    const val DOWNLOAD_ACCELERATOR_URL = "https://gh-proxy.com/"

    // 供浏览器打开的发布页面 (也可以放在 core 的 AppConfig，但放在这里跟其他 URL 一起管理也可以)
    const val LATEST_RELEASE_URL =
        "https://github.com/youfeng11/SFS-CustomTranslations-Installer/releases/latest"
}