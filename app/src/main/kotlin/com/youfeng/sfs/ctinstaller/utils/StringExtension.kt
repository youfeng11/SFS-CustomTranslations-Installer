package com.youfeng.sfs.ctinstaller.utils

import kotlinx.serialization.json.Json

fun String.isValidJson(): Boolean = try {
    Json.parseToJsonElement(this)
    true
} catch (_: Exception) {
    false
}