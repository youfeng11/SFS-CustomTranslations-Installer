package com.youfeng.sfs.ctinstaller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TranslationsApi(
    val file: String? = null,
    val lang: String? = null,
    val author: String? = null
)