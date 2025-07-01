package com.youfeng.sfs.ctinstaller.utils

import android.util.Patterns
import kotlinx.serialization.json.Json

fun String.isUrl(): Boolean {
    return Patterns.WEB_URL.matcher(this).matches()
}

fun String.isValidJson(): Boolean = try {
    Json.parseToJsonElement(this)
    true
} catch (_: Exception) {
    false
}