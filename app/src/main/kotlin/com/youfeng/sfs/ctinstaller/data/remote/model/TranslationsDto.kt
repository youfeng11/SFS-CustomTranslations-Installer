package com.youfeng.sfs.ctinstaller.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class TranslationsDto(
    val file: String? = null,
    val lang: String? = null,
    val author: String? = null
)