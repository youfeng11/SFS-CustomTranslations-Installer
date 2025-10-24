package com.youfeng.sfs.ctinstaller.core

import android.os.Environment

object Constants {
    val externalStorage: String =
        Environment.getExternalStorageDirectory().absolutePath

    const val ANDROID_DATA_DIRECTORY = "Android/data"

    const val SFS_PACKAGE_NAME = "com.StefMorojna.SpaceflightSimulator"

    const val SFS_DATA_DIRECTORY = "$ANDROID_DATA_DIRECTORY/$SFS_PACKAGE_NAME"

    const val SFS_CUSTOM_TRANSLATION_DIRECTORY = "$SFS_DATA_DIRECTORY/files/Custom Translations/"

    const val API_URL = "https://gitee.com/YouFeng11/SFS-zh-CN-Translation/raw/master/info.json"

    const val TRANSLATIONS_API_URL =
        "https://raw.githubusercontent.com/youfeng11/SFS-CustomTranslations-Installer/refs/heads/main/api/translations.json"

    const val UPDATE_API_URL =
        "https://api.github.com/repos/youfeng11/SFS-CustomTranslations-Installer/releases/latest"

    const val LATEST_RELEASE_URL =
        "https://github.com/youfeng11/SFS-CustomTranslations-Installer/releases/latset"

    const val DOWNLOAD_ACCELERATOR_URL = "https://get.2sb.org/"
}
