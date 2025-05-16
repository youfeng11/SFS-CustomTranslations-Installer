package com.youfeng.sfs.ctinstaller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomTranslationInfo(
    val url: String? = null,
    val compatibleVersion: String? = null,
    val versionCode: Int? = null
)
