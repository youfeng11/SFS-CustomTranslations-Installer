package com.youfeng.sfs.ctinstaller.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LatestReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String
)
