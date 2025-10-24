package com.youfeng.sfs.ctinstaller.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LatestReleaseApi(
    @SerialName("tag_name") val tagName: String,
    val name: String
)
