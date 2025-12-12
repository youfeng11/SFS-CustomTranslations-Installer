package com.youfeng.sfs.ctinstaller.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomTranslationInfoDto(
    val url: String? = null,
    val compatibleVersion: String? = null
)
